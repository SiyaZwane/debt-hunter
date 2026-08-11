package com.debthunter.integration;

import com.debthunter.domain.Finding;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** An in-memory {@link WorkItemProvider} recording every call it receives, for test assertions. */
final class FakeWorkItemProvider implements WorkItemProvider {

  private final AtomicInteger nextId = new AtomicInteger(1);
  final List<Finding> created = new ArrayList<>();
  final List<String> closed = new ArrayList<>();
  final List<String> reopened = new ArrayList<>();

  @Override
  public String createItem(Finding finding) {
    created.add(finding);
    return "ITEM-" + nextId.getAndIncrement();
  }

  @Override
  public void closeItem(String externalId) {
    closed.add(externalId);
  }

  @Override
  public void reopenItem(String externalId) {
    reopened.add(externalId);
  }
}
