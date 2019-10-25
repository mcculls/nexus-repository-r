/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2017-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.r.internal;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.entity.EntityId;
import org.sonatype.nexus.common.entity.EntityMetadata;
import org.sonatype.nexus.common.event.EventManager;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.AssetUpdatedEvent;
import org.sonatype.nexus.repository.storage.Bucket;
import org.sonatype.nexus.repository.storage.BucketStore;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.ComponentDirector;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @since 1.next
 */
@Named(RFormat.NAME)
@Singleton
public class RComponentDirector
    extends ComponentSupport
    implements ComponentDirector
{
  private final BucketStore bucketStore;

  private final RepositoryManager repositoryManager;

  private final EventManager eventManager;

  private String sourceRepositoryName;

  @Inject
  public RComponentDirector(final BucketStore bucketStore,
                            final RepositoryManager repositoryManager,
                            final EventManager eventManager)
  {
    this.bucketStore = checkNotNull(bucketStore);
    this.repositoryManager = checkNotNull(repositoryManager);
    this.eventManager = checkNotNull(eventManager);
  }

  @Override
  public boolean allowMoveTo(final Repository destination) {
    return true;
  }

  @Override
  public boolean allowMoveTo(final Component component, final Repository destination) {
    return repositoryFor(component).isPresent();
  }

  @Override
  public boolean allowMoveFrom(final Repository source) {
    return true;
  }

  private Optional<Repository> repositoryFor(final Component component) {
    return Optional.of(component)
        .map(Component::bucketId)
        .map(bucketStore::getById)
        .map(Bucket::getRepositoryName)
        .map(repositoryManager::get);
  }

  @Override
  public Component beforeMove(final Component component,
                              final List<Asset> assets,
                              final Repository source,
                              final Repository destination)
  {
    sourceRepositoryName = source.getName();
    return component;
  }

  @Override
  public Component afterMove(final Component component, final Repository destination) {
    EntityMetadata entityMetadata = component.getEntityMetadata();
    assert entityMetadata != null;
    EntityId componentId = entityMetadata.getId();

    if (sourceRepositoryName != null) {
      eventManager.post(new AssetUpdatedEvent(entityMetadata, sourceRepositoryName, componentId));
      sourceRepositoryName = null;
    }
    eventManager.post(new AssetUpdatedEvent(entityMetadata, destination.getName(),componentId));
    return component;
  }
}
