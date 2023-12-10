package net.taskwolf.access.condition;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.condition.ConditionInformation;
import net.taskwolf.core.condition.ConditionInformationRepository;
import net.taskwolf.core.user.UserDatabaseTable;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.Map;

@RestController
public final class ConditionController extends TaskwolfRestController {
  private final ConditionInformationRepository conditionRepository;

  private ConditionController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    ConditionInformationRepository conditionRepository
  ) {
    super(secretKey, userDatabaseTable);
    this.conditionRepository = conditionRepository;
  }

  @RequestMapping(path = "/conditions/find/", method = RequestMethod.GET)
  public Map<String, Object> findConditions(
    @RequestBody Map<String, Object> input
  ) {
    var conditions = conditionRepository.findAll();
    var information = Lists.<Map<String, Object>>newArrayList();
    for (var condition : conditions) {
      information.add(findConditionInformation(condition));
    }
    return Map.of("conditions", information);
  }

  @RequestMapping(path = "/condition/find/", method = RequestMethod.POST)
  public Map<String, Object> findCondition(
    @RequestBody Map<String, Object> input
  ) {
    var identifier = (String) input.get("identifier");
    var condition = conditionRepository.findByIdentifier(identifier);
    return condition.map(this::findConditionInformation)
      .orElseGet(Maps::newHashMap);
  }

  private Map<String, Object> findConditionInformation(ConditionInformation condition) {
    var information = Maps.<String, Object>newHashMap();
    information.put("identifier", condition.identifier());
    information.put("name", condition.name());
    information.put("dataType", condition.dataType());
    return information;
  }
}
