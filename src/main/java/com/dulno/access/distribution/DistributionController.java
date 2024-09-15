package com.dulno.access.distribution;

import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.worker.WorkerConfiguration;
import com.dulno.core.worker.WorkerDistribution;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;

@RestController
public final class DistributionController extends DulnoRestController {
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
    var body = DulnoRequestBody.of(payload, response);
    if (!body.getString("key").equals(configuration.distributionKey())) {
      return;
    }
    distribution.addUser(body.getUUID("user"));
  }
}