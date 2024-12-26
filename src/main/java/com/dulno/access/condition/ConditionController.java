package com.dulno.access.condition;

import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.workflow.condition.ConditionInformation;
import com.dulno.workflow.condition.ConditionInformationRepository;
import com.dulno.core.locale.Translation;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ConditionController extends DulnoRestController {
  private final Translation translation;
  private final ConditionInformationRepository conditionRepository;

  private ConditionController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    Translation translation,
    ConditionInformationRepository conditionRepository
  ) {
    super(secretKey, userDatabaseTable);
    this.translation = translation;
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
    var body = DulnoRequestBody.of(payload, response);
    var condition = conditionRepository.findByIdentifier(body.getString("identifier"));
    return findUser(request).thenApply(user -> condition.map(value ->
      findConditionInformation(user, value)).orElseGet(Maps::newHashMap));
  }

  private Map<String, Object> findConditionInformation(
    User user, ConditionInformation condition
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("identifier", condition.identifier());
    information.put("name", translation.translate(user, condition.name()));
    information.put("dataType", condition.dataType());
    return information;
  }
}
