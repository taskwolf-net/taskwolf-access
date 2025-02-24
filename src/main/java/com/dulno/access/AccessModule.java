package com.dulno.access;

import com.dulno.core.error.ErrorRepository;
import com.dulno.core.hashing.Hashing;
import com.dulno.core.worker.WorkerConfiguration;
import com.dulno.workflow.WorkflowModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.dulno.access.activity.ActivityContextInitializer;
import com.dulno.access.bundle.BundleContextInitializer;
import com.dulno.access.maintenance.MaintenanceContextInitializer;
import com.dulno.access.offer.OfferContextInitializer;
import com.dulno.access.organization.OrganizationContextInitializer;
import com.dulno.access.question.QuestionContextInitializer;
import com.dulno.access.sale.SaleContextInitializer;
import com.dulno.access.setting.SettingContextInitializer;
import com.dulno.access.stripe.StripeContextInitializer;
import com.dulno.access.target.TargetContextInitializer;
import com.dulno.access.template.TemplateContextInitializer;
import com.dulno.access.ticket.TicketContextInitializer;
import com.dulno.access.trial.TrialContextInitializer;
import com.dulno.access.tutorial.TutorialContextInitializer;
import com.dulno.access.verification.VerificationContextInitializer;
import com.dulno.access.whitelist.WhitelistContextInitializer;
import com.dulno.access.workflow.WorkflowContextInitializer;
import com.dulno.core.database.DatabaseConnection;
import com.dulno.core.database.DatabaseKeyspace;
import com.dulno.core.locale.Translation;
import com.dulno.core.log.Log;
import com.dulno.core.mail.MailFactory;
import com.dulno.core.module.*;
import com.dulno.core.module.Module;
import com.dulno.core.worker.WorkerDistribution;
import com.dulno.core.worker.client.WorkerProxyClient;
import org.springframework.boot.SpringApplication;

@ModuleDescription(name = "access", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.HIGH)
public final class AccessModule extends Module {
  private Log log;

  public AccessModule(Injector injector) {
    super(injector);
  }

  @Override
  public void enable() {
    System.setProperty("jdk.httpclient.allowRestrictedHeaders",
      "host,connection,content-length,upgrade");
    log = injector().getInstance(Log.class).subLog("Access");
    registerContextInitializers(injector().getInstance(SpringApplication.class));
  }

  private void registerContextInitializers(SpringApplication application) {
    application.addInitializers(AccessContextInitializer.create(log,
      injector().getInstance(Key.get(java.security.Key.class, Names.named("homeKey"))),
      injector().getInstance(Key.get(java.security.Key.class, Names.named("productKey"))),
      injector().getInstance(Key.get(java.security.Key.class, Names.named("refreshKey"))),
      injector().getInstance(ModuleLoader.class),
      injector().getInstance(DatabaseConnection.class),
      injector().getInstance(DatabaseKeyspace.class),
      injector().getInstance(WorkerDistribution.class),
      injector().getInstance(WorkerProxyClient.class),
      injector().getInstance(WorkerConfiguration.class),
      injector().getInstance(WorkflowModule.class),
      injector().getInstance(Translation.class),
      injector().getInstance(ErrorRepository.class),
      injector().getInstance(MailFactory.class),
      injector().getInstance(Hashing.class)));
    application.addInitializers(injector().getInstance(WhitelistContextInitializer.class));
    application.addInitializers(injector().getInstance(MaintenanceContextInitializer.class));
    application.addInitializers(injector().getInstance(VerificationContextInitializer.class));
    application.addInitializers(injector().getInstance(StripeContextInitializer.class));
    application.addInitializers(injector().getInstance(TrialContextInitializer.class));
    application.addInitializers(injector().getInstance(TargetContextInitializer.class));
    application.addInitializers(injector().getInstance(WorkflowContextInitializer.class));
    application.addInitializers(injector().getInstance(TemplateContextInitializer.class));
    application.addInitializers(injector().getInstance(OrganizationContextInitializer.class));
    application.addInitializers(injector().getInstance(BundleContextInitializer.class));
    application.addInitializers(injector().getInstance(OfferContextInitializer.class));
    application.addInitializers(injector().getInstance(TutorialContextInitializer.class));
    application.addInitializers(injector().getInstance(TicketContextInitializer.class));
    application.addInitializers(injector().getInstance(SettingContextInitializer.class));
    application.addInitializers(injector().getInstance(ActivityContextInitializer.class));
    application.addInitializers(injector().getInstance(QuestionContextInitializer.class));
    application.addInitializers(injector().getInstance(SaleContextInitializer.class));
  }

  @Override
  public void disable() {

  }

  @Override
  public ModuleInformation moduleInformation() {
    return ModuleInformation.create("Access", "", "",
      ModuleInformation.Type.HIDDEN);
  }
}
