package com.tap.backend.agent;

import com.tap.backend.domain.agent.AgentJobEntity;
import com.tap.backend.domain.agent.AgentJobStatus;
import com.tap.backend.repo.AgentJobRepository;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AgentScheduler {
  private final AgentJobRepository agentJobRepository;
  private final AgentJobRunner agentJobRunner;
  private final AgentProperties props;
  private final ExecutorService jobExecutor;
  private final Semaphore jobSlots;

  public AgentScheduler(AgentJobRepository agentJobRepository, AgentJobRunner agentJobRunner, AgentProperties props) {
    this.agentJobRepository = agentJobRepository;
    this.agentJobRunner = agentJobRunner;
    this.props = props;
    this.jobExecutor = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r);
      t.setName("tap-agent-job-" + t.getId());
      t.setDaemon(true);
      return t;
    });
    int maxJobs = props.jobMaxConcurrency() <= 0 ? 1 : props.jobMaxConcurrency();
    this.jobSlots = new Semaphore(maxJobs);
  }

  @PreDestroy
  public void shutdown() {
    jobExecutor.shutdown();
  }

  @Scheduled(fixedDelayString = "${tap.agent.poll-interval-ms:2000}")
  public void poll() {
    if (!jobSlots.tryAcquire()) return;
    jobExecutor.execute(() -> {
      try {
        Long claimed = claimNextPendingJobId();
        if (claimed != null) {
          agentJobRunner.runJob(claimed);
        }
      } finally {
        jobSlots.release();
      }
    });
  }

  @Transactional
  protected Long claimNextPendingJobId() {
    AgentJobEntity job;
    try {
      job = agentJobRepository.findFirstByStatusOrderByCreatedAtAsc(AgentJobStatus.PENDING);
    } catch (Exception e) {
      return null;
    }
    if (job == null) return null;
    job.setStatus(AgentJobStatus.RUNNING);
    job.setStartedAt(Instant.now());
    job.setFinishedAt(null);
    job.setErrorMessage(null);
    job.setProgress(Math.max(job.getProgress(), 1));
    agentJobRepository.save(job);
    return job.getId();
  }
}
