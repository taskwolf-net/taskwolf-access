package net.taskwolf.access.distribution;

import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.worker.WorkerConfiguration;
import net.taskwolf.core.worker.WorkerDistribution;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;

@RestController
public final class DistributionController extends TaskwolfRestController {
  private final WorkerDistribution distribution;
  private final WorkerConfiguration configuration;

  private DistributionController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    WorkerDistribution distribution, WorkerConfiguration configuration
  ) {
    super(secretKey, userDatabaseTable);
    this.distribution = distribution;
    this.configuration = configuration;
  }

  @RequestMapping(path = "/distribution/add/", method = RequestMethod.POST)
  public void addDistributionUser(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    if (!body.getString("key").equals(configuration.distributionKey())) {
      return;
    }
    distribution.addUser(body.getUUID("user"));
  }
}