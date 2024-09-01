package net.taskwolf.access;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import net.taskwolf.access.activity.ActivityContextInitializer;
import net.taskwolf.access.bundle.BundleContextInitializer;
import net.taskwolf.access.maintenance.MaintenanceContextInitializer;
import net.taskwolf.access.offer.OfferContextInitializer;
import net.taskwolf.access.organization.OrganizationContextInitializer;
import net.taskwolf.access.question.QuestionContextInitializer;
import net.taskwolf.access.sale.SaleContextInitializer;
import net.taskwolf.access.setting.SettingContextInitializer;
import net.taskwolf.access.stripe.StripeContextInitializer;
import net.taskwolf.access.target.TargetContextInitializer;
import net.taskwolf.access.template.TemplateContextInitializer;
import net.taskwolf.access.ticket.TicketContextInitializer;
import net.taskwolf.access.trial.TrialContextInitializer;
import net.taskwolf.access.tutorial.TutorialContextInitializer;
import net.taskwolf.access.verification.VerificationContextInitializer;
import net.taskwolf.access.whitelist.WhitelistContextInitializer;
import net.taskwolf.access.workflow.WorkflowContextInitializer;
import net.taskwolf.core.CoreModule;
import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;
import net.taskwolf.core.log.Log;
import net.taskwolf.core.mail.MailFactory;
import net.taskwolf.core.module.*;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.worker.WorkerDistribution;
import net.taskwolf.core.worker.client.WorkerProxyClient;
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
      injector().getInstance(CoreModule.class),
      injector().getInstance(MailFactory.class)));
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
