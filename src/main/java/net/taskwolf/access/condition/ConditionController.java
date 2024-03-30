package net.taskwolf.access.condition;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.condition.ConditionInformation;
import net.taskwolf.core.condition.ConditionInformationRepository;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ConditionController extends TaskwolfRestController {
  private final CoreModule coreModule;
  private final ConditionInformationRepository conditionRepository;

  private ConditionController(
    Key secretKey, UserDatabaseTable userDatabaseTable, CoreModule coreModule,
    ConditionInformationRepository conditionRepository
  ) {
    super(secretKey, userDatabaseTable);
    this.coreModule = coreModule;
    this.conditionRepository = conditionRepository;
  }

  @RequestMapping(path = "/conditions/find/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findConditions(
    HttpServletRequest request
  ) {
    var conditions = conditionRepository.findAll();
    return findUser(request).thenApply(user -> Map.of("conditions",
      conditions.stream().map(condition -> findConditionInformation(user, condition))));
  }

  @RequestMapping(path = "/condition/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findCondition(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var condition = conditionRepository.findByIdentifier(body.getString("identifier"));
    return findUser(request).thenApply(user -> condition.map(value ->
      findConditionInformation(user, value)).orElseGet(Maps::newHashMap));
  }

  private Map<String, Object> findConditionInformation(
    User user, ConditionInformation condition
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("identifier", condition.identifier());
    information.put("name", coreModule.translate(user, condition.name()));
    information.put("dataType", condition.dataType());
    return information;
  }
}
