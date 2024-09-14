package com.dulno.access.organization;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.organization.Organization;
import com.dulno.core.organization.OrganizationDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;

import java.security.Key;
import java.util.UUID;
import java.util.function.Consumer;

@Accessors(fluent = true)
public class OrganizationController extends DulnoRestController {
  @Getter(AccessLevel.PROTECTED)
  private final OrganizationDatabaseTable organizationDatabaseTable;
  private final UserTargetDatabaseTable targetDatabaseTable;

  protected OrganizationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    OrganizationDatabaseTable organizationDatabaseTable,
    UserTargetDatabaseTable targetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.organizationDatabaseTable = organizationDatabaseTable;
    this.targetDatabaseTable = targetDatabaseTable;
  }

  protected void performOrganizationOwnerOperation(
    UUID userId, Consumer<Organization> operation,
    Runnable failResponse
  ) {
    userDatabaseTable().findUser(userId).thenAccept(user ->
      targetDatabaseTable.findTargetSecured(userId).thenAccept(target ->
        performOrganizationOwnerOperation(user, target, operation,
          failResponse)));
  }

  private void performOrganizationOwnerOperation(
    User user, UUID targetId, Consumer<Organization> operation,
    Runnable failResponse
  ) {
    if (!user.organizations().contains(targetId)) {
      failResponse.run();
      return;
    }
    organizationDatabaseTable.findOrganization(targetId)
      .thenAccept(organization -> performOrganizationOwnerOperation(user,
        organization, operation, failResponse));
  }

  private void performOrganizationOwnerOperation(
    User user, Organization organization, Consumer<Organization> operation,
    Runnable failResponse
  ) {
    if (!organization.owner().equals(user.id())) {
      failResponse.run();
      return;
    }
    operation.accept(organization);
  }

  protected void performOrganizationMemberOperation(
    UUID userId, Consumer<Organization> operation,
    Runnable failResponse
  ) {
    userDatabaseTable().findUser(userId).thenAccept(user ->
      targetDatabaseTable.findTargetSecured(userId).thenAccept(target ->
        performOrganizationMemberOperation(user, target, operation,
          failResponse)));
  }

  private void performOrganizationMemberOperation(
    User user, UUID targetId, Consumer<Organization> operation,
    Runnable failResponse
  ) {
    if (!user.organizations().contains(targetId)) {
      failResponse.run();
      return;
    }
    organizationDatabaseTable.findOrganization(targetId)
      .thenAccept(operation::accept);
  }

  protected void performOrganizationOperation(
    UUID userId, Consumer<Organization> operation,
    Runnable failResponse
  ) {
    targetDatabaseTable.findTargetSecured(userId).thenAccept(target ->
      organizationDatabaseTable.organizationExists(target).thenAccept(exists ->
        performOrganizationOperation(target, exists, operation,
          failResponse)));
  }

  private void performOrganizationOperation(
    UUID targetId, boolean exists, Consumer<Organization> operation,
    Runnable failResponse
  ) {
    if (!exists) {
      failResponse.run();
      return;
    }
    organizationDatabaseTable.findOrganization(targetId)
      .thenAccept(operation::accept);
  }
}
