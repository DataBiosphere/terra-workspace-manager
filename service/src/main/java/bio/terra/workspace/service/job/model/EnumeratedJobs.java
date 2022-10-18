package bio.terra.workspace.service.job.model;

import java.util.List;

public class EnumeratedJobs {
  private int totalResults;
  private String pageToken;
  private List<EnumeratedJob> results;

  public int getTotalResults() {
    return totalResults;
  }

  public EnumeratedJobs totalResults(int totalResults) {
    this.totalResults = totalResults;
    return this;
  }

  public String getPageToken() {
    return pageToken;
  }

  public EnumeratedJobs pageToken(String pageToken) {
    this.pageToken = pageToken;
    return this;
  }

  public List<EnumeratedJob> getResults() {
    return results;
  }

  public EnumeratedJobs results(List<EnumeratedJob> results) {
    this.results = results;
    return this;
  }
}
