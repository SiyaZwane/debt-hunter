package com.debthunter.integration;

import com.debthunter.domain.Finding;

/** The narrow set of actions a tracker backend must support to hold a published work item. */
public interface WorkItemProvider {

  /**
   * Creates a new tracker item for {@code finding}.
   *
   * @param finding the finding to track
   * @return the backend's identifier for the created item
   */
  String createItem(Finding finding);

  /**
   * Closes the tracker item identified by {@code externalId}, because the finding it tracks no
   * longer appears.
   *
   * @param externalId the backend's identifier for the item to close
   */
  void closeItem(String externalId);

  /**
   * Reopens the tracker item identified by {@code externalId}, because the finding it tracks has
   * reappeared.
   *
   * @param externalId the backend's identifier for the item to reopen
   */
  void reopenItem(String externalId);
}
