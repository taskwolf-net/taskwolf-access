package net.taskwolf.access.tutorial;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.tutorial.Tutorial;
import net.taskwolf.core.tutorial.TutorialDatabaseTable;
import net.taskwolf.core.tutorial.level.TutorialLevelRegistry;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TutorialController extends TaskwolfRestController {
  private final TutorialDatabaseTable tutorialDatabaseTable;
  private final TutorialLevelRegistry tutorialLevelRegistry;
  private final CoreModule coreModule;

  private TutorialController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TutorialDatabaseTable tutorialDatabaseTable,
    TutorialLevelRegistry tutorialLevelRegistry, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable);
    this.tutorialDatabaseTable = tutorialDatabaseTable;
    this.tutorialLevelRegistry = tutorialLevelRegistry;
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/tutorial/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findTutorialState(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(user ->
      tutorialDatabaseTable.tutorialExists(user.id())
        .thenCompose(exists -> findTutorialState(user, exists)));
  }

  private CompletableFuture<Map<String, Object>> findTutorialState(
    User user, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Map.of("active", false));
    }
    return tutorialDatabaseTable.findTutorial(user.id())
      .thenApply(tutorial -> findTutorialState(user, tutorial));
  }

  private Map<String, Object> findTutorialState(
    User user, Tutorial tutorial
  ) {
    var level = tutorialLevelRegistry.findLevel(tutorial.level());
    var step = level.steps().get(tutorial.step());
    var state = Maps.<String, Object>newHashMap();
    var isFinished = tutorial.level() == tutorialLevelRegistry.size() &&
      tutorial.step() == level.steps().size() - 1;
    state.put("active", true);
    state.put("page", level.page());
    state.put("title", coreModule.translate(user, step.title()));
    state.put("description", coreModule.translate(user, step.description()));
    state.put("element", step.element());
    state.put("position", step.position());
    state.put("shiftContentDown", step.shiftContentDown());
    state.put("progress", tutorialLevelRegistry.findStepProgress(step));
    state.put("allSteps", tutorialLevelRegistry.findStepNumber());
    state.put("continue", isFinished ? coreModule.translate(user, "tutorial.finish") :
      coreModule.translate(user, "tutorial.continue"));
    state.put("cancel", coreModule.translate(user, "tutorial.cancel"));
    return state;
  }

  @RequestMapping(path = "/tutorial/next/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> nextTutorialStep(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(user ->
      tutorialDatabaseTable.tutorialExists(user.id())
        .thenCompose(exists -> nextTutorialStep(user, exists)));
  }

  private CompletableFuture<Map<String, Object>> nextTutorialStep(
    User user, boolean exists
  ) {
    if (!exists) {
      return CompletableFuture.completedFuture(Map.of("active", false));
    }
    return tutorialDatabaseTable.findTutorial(user.id())
      .thenApply(tutorial -> nextTutorialStep(user, tutorial));
  }

  private Map<String, Object> nextTutorialStep(
    User user, Tutorial tutorial
  ) {
    var level = tutorialLevelRegistry.findLevel(tutorial.level());
    if (tutorial.step() + 1 < level.steps().size()) {
      tutorial.updateStep(tutorial.step() + 1);
      tutorialDatabaseTable.updateTutorial(tutorial);
      return findTutorialState(user, tutorial);
    }
    if (tutorial.level() + 1 > tutorialLevelRegistry.size()) {
      tutorialDatabaseTable.deleteTutorial(user.id());
      return Map.of("active", false);
    }
    tutorial.updateLevel(tutorial.level() + 1);
    tutorial.updateStep(0);
    tutorialDatabaseTable.updateTutorial(tutorial);
    return findTutorialState(user, tutorial);
  }

  @RequestMapping(path = "/tutorial/cancel/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> cancelTutorial(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(user ->
      tutorialDatabaseTable.tutorialExists(user.id())
        .thenApply(exists -> cancelTutorial(user, exists)));
  }

  private Map<String, Object> cancelTutorial(
    User user, boolean exists
  ) {
    if (!exists) {
      return Map.of("success", false);
    }
    tutorialDatabaseTable.deleteTutorial(user.id());
    return Map.of("success", true);
  }

  @RequestMapping(path = "/tutorial/restart/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> restartTutorial(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(user ->
      tutorialDatabaseTable.tutorialExists(user.id())
        .thenApply(exists -> restartTutorial(user, exists)));
  }

  private Map<String, Object> restartTutorial(
    User user, boolean exists
  ) {
    var tutorial = Tutorial.create(user.id(), 0, 0);
    if (exists) {
      tutorialDatabaseTable.updateTutorial(tutorial);
    } else {
      tutorialDatabaseTable.insertTutorial(tutorial);
    }
    return Map.of("success", true);
  }
}
