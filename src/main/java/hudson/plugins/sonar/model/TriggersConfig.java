/*
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package hudson.plugins.sonar.model;

import hudson.Util;
import hudson.model.BuildBadgeAction;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.plugins.sonar.BuildSonarAction;
import hudson.plugins.sonar.Messages;
import hudson.triggers.SCMTrigger;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Evgeny Mandrikov
 * @since 1.2
 */
public class TriggersConfig implements Serializable {

  private boolean skipScmCause;

  private boolean skipUpstreamCause;

  /**
   * @since 1.7
   */
  private String envVar;

  /**
   * @since 1.9-SNAPSHOT
   */
  private String skipTimeout;

  public String getSkipTimeout() {
    return skipTimeout;
  }

  public void setSkipTimeout(String skipTimeout) {
    this.skipTimeout = skipTimeout;
  }

  public TriggersConfig() {
  }

  @DataBoundConstructor
  public TriggersConfig(boolean skipScmCause, boolean skipUpstreamCause, String envVar, String skipTimeout) {
    this.skipScmCause = skipScmCause;
    this.skipUpstreamCause = skipUpstreamCause;
    this.envVar = envVar;
    // TODO : form validation
    // https://wiki.jenkins-ci.org/display/JENKINS/Basic+guide+to+Jelly+usage+in+Jenkins#BasicguidetoJellyusageinJenkins-Formvalidation
    this.skipTimeout = skipTimeout;
  }

  private long toLong(String skipTimeoutString) {
    try {
      return Long.parseLong(skipTimeoutString);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  public boolean isSkipScmCause() {
    return skipScmCause;
  }

  public void setSkipScmCause(boolean b) {
    this.skipScmCause = b;
  }

  public boolean isSkipUpstreamCause() {
    return skipUpstreamCause;
  }

  public void setSkipUpstreamCause(boolean b) {
    this.skipUpstreamCause = b;
  }

  public String getEnvVar() {
    return Util.fixEmptyAndTrim(envVar);
  }

  public void setEnvVar(String envVar) {
    this.envVar = envVar;
  }

  public long getSkipTimeoutInMillis() {
    return toLong(skipTimeout) * 60 * 1000;
  }

  public String isSkipSonar(AbstractBuild<?, ?> build) {
    Result result = build.getResult();

    long lastBuildInMillis = getLastSonarBuildTimeInMillis(build);
    long elapsed = System.currentTimeMillis() - lastBuildInMillis;
    if (elapsed < getSkipTimeoutInMillis()) {
      return Messages.SonarPublisher_TimeoutNotElapsed(millisToHumanReadable(elapsed), millisToHumanReadable(getSkipTimeoutInMillis()));
    }
    if (result != null) {
      // skip analysis if build failed
      // unstable means that build completed, but there were some test failures, which is not critical for analysis
      if (result.isWorseThan(Result.UNSTABLE)) {
        return Messages.SonarPublisher_BadBuildStatus(build.getResult().toString());
      }
    }

    // skip analysis by environment variable
    if (getEnvVar() != null) {
      String value = build.getBuildVariableResolver().resolve(getEnvVar());
      if ("true".equalsIgnoreCase(value)) {
        return Messages.Skipping_Sonar_analysis();
      }
    }

    // skip analysis, when all causes from blacklist
    List<Cause> causes = new ArrayList<Cause>(build.getCauses());
    Iterator<Cause> iter = causes.iterator();
    while (iter.hasNext()) {
      Cause cause = iter.next();
      if (SCMTrigger.SCMTriggerCause.class.isInstance(cause) && isSkipScmCause()) {
        iter.remove();
      } else if (Cause.UpstreamCause.class.isInstance(cause) && isSkipUpstreamCause()) {
        iter.remove();
      }
    }
    return causes.isEmpty() ? Messages.Skipping_Sonar_analysis() : null;
  }

  /**
   * @param build the build to analyze.
   * @return 0 if no previous sonar build was found.
   */
  private long getLastSonarBuildTimeInMillis(AbstractBuild<?, ?> build) {
    AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
    if (previousBuild != null) {
      for (BuildBadgeAction buildBadgeAction : build.getPreviousBuild().getBadgeActions()) {
        if (BuildSonarAction.class.getName().equals(buildBadgeAction.getClass().getName())) {
          return previousBuild.getTimeInMillis();
        }
      }
      return getLastSonarBuildTimeInMillis(previousBuild);
    }
    return 0;
  }

  private Object millisToHumanReadable(long elapsed) {
    return elapsed / 1000 + " s";
  }

  /**
   * For internal use only.
   */
  public static class SonarCause extends Cause {
    @Override
    public String getShortDescription() {
      return null;
    }
  }
}
