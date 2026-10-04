package net.taskwolf.access.tutorial;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.locale.Translation;
import net.taskwolf.core.tutorial.Tutorial;
import net.taskwolf.core.tutorial.TutorialDatabaseTable;
import net.taskwolf.core.tutorial.level.TutorialLevel;
import net.taskwolf.core.tutorial.level.TutorialLevelRegistry;
import net.taskwolf.core.tutorial.level.account.AccountsTutorialLevel;
import net.taskwolf.core.tutorial.level.bundle.BundleTutorialLevel;
import net.taskwolf.core.tutorial.level.organization.OrganizationMembersTutorialLevel;
import net.taskwolf.core.tutorial.level.organization.OrganizationTeamsTutorialLevel;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class TutorialController extends TaskwolfRestController {
  private final TutorialDatabaseTable tutorialDatabaseTable;
  private final TutorialLevelRegistry tutorialLevelRegistry;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final Translation translation;

  private TutorialController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    TutorialDatabaseTable tutorialDatabaseTable,
    TutorialLevelRegistry tutorialLevelRegistry,
    UserTargetDatabaseTable userTargetDatabaseTable, Translation translation
  ) {
    super(secretKey, userDatabaseTable);
    this.tutorialDatabaseTable = tutorialDatabaseTable;
    this.tutorialLevelRegistry = tutorialLevelRegistry;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.translation = translation;
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
      .thenCompose(tutorial -> userTargetDatabaseTable.findTargetSecured(user.id())
        .thenApply(target -> findTutorialState(user, target, tutorial)));
  }

  private Map<String, Object> findTutorialState(
    User user, UUID target, Tutorial tutorial
  ) {
    var level = tutorialLevelRegistry.findLevelByIndex(tutorial.level());
    var step = level.steps().get(tutorial.step());
    var state = Maps.<String, Object>newHashMap();
    var isFinished = tutorial.level() == tutorialLevelRegistry.size() &&
      tutorial.step() == level.steps().size() - 1;
    state.put("active", true);
    state.put("page", level.page());
    state.put("title", translation.translate(user, step.title()));
    state.put("description", translation.translate(user, step.description()));
    state.put("element", step.element());
    state.put("position", step.position());
    state.put("shiftContentDown", step.shiftContentDown());
    var exclusions = findTutorialLevelExclusions(user, target);
    state.put("progress", tutorialLevelRegistry.findStepProgress(step, exclusions));
    state.put("allSteps", tutorialLevelRegistry.findStepNumber(exclusions));
    state.put("continue", isFinished ? translation.translate(user, "tutorial.finish") :
      translation.translate(user, "tutorial.continue"));
    state.put("cancel", translation.translate(user, "tutorial.cancel"));
    return state;
  }

  private Class<? extends TutorialLevel>[] findTutorialLevelExclusions(
    User user, UUID target
  ) {
    if (user.id().equals(target)) {
      return new Class[] {OrganizationMembersTutorialLevel.class,
        OrganizationTeamsTutorialLevel.class};
    }
    return new Class[] {BundleTutorialLevel.class};
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
      .thenCompose(tutorial -> userTargetDatabaseTable.findTargetSecured(user.id())
        .thenApply(target -> nextTutorialStep(user, target, tutorial)));
  }

  private Map<String, Object> nextTutorialStep(
    User user, UUID target, Tutorial tutorial
  ) {
    var level = tutorialLevelRegistry.findLevelByIndex(tutorial.level());
    if (tutorial.step() + 1 < level.steps().size()) {
      tutorial.updateStep(tutorial.step() + 1);
      tutorialDatabaseTable.updateTutorial(tutorial);
      return findTutorialState(user, target, tutorial);
    }
    if (tutorial.level() + 1 > tutorialLevelRegistry.size()) {
      tutorialDatabaseTable.deleteTutorial(user.id());
      return Map.of("active", false);
    }
    tutorial.updateLevel(tutorial.level() +
      calculateNextLevelOffset(user, target, level));
    tutorial.updateStep(0);
    tutorialDatabaseTable.updateTutorial(tutorial);
    return findTutorialState(user, target, tutorial);
  }

  private int calculateNextLevelOffset(
    User user, UUID target, TutorialLevel currentLevel
  ) {
    if (currentLevel instanceof AccountsTutorialLevel) {
      return user.id().equals(target) ? 1 : 2;
    }
    if (currentLevel instanceof BundleTutorialLevel) {
      return 3;
    }
    return 1;
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
