/**
 *
 */
package hudson.plugins.skype.im.transport;

import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMMessageTargetConversionException;
import hudson.plugins.im.IMMessageTargetConverter;
import hudson.plugins.im.IMPublisherDescriptor;
import hudson.plugins.im.MatrixJobMultiplier;
import hudson.plugins.im.NotificationStrategy;
import hudson.plugins.im.build_notify.BuildToChatNotifier;
import hudson.plugins.im.config.ParameterNames;
import hudson.plugins.im.tools.ExceptionHelper;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.util.Assert;

public class SkypePublisherDescriptor extends BuildStepDescriptor<Publisher> implements IMPublisherDescriptor {

    private static final Logger LOGGER = Logger.getLogger(SkypePublisherDescriptor.class.getName());
    private static final String PREFIX = "skypePlugin.";
    public static final String PARAMETERNAME_ENABLED = SkypePublisherDescriptor.PREFIX + "enabled";
    public static final String PARAMETERNAME_PRESENCE = SkypePublisherDescriptor.PREFIX + "exposePresence";
    public static final String PARAMETERNAME_GROUP_NICKNAME = SkypePublisherDescriptor.PREFIX + "groupNick";
    public static final String PARAMETERNAME_TARGETS = SkypePublisherDescriptor.PREFIX + "targets";
    public static final String PARAMETERNAME_STRATEGY = SkypePublisherDescriptor.PREFIX + "strategy";
    public static final String PARAMETERNAME_INITIAL_GROUPCHATS = SkypePublisherDescriptor.PREFIX + "initialGroupChats";
    public static final String PARAMETERNAME_COMMAND_PREFIX = SkypePublisherDescriptor.PREFIX + "commandPrefix";
    public static final String PARAMETERNAME_HUDSON_LOGIN = SkypePublisherDescriptor.PREFIX + "hudsonLogin";
    public static final String PARAMETERNAME_HUDSON_PASSWORD = SkypePublisherDescriptor.PREFIX + "hudsonPassword";
    public static final String[] PARAMETERVALUE_STRATEGY_VALUES = NotificationStrategy.getDisplayNames();
    public static final String PARAMETERVALUE_STRATEGY_DEFAULT = NotificationStrategy.STATECHANGE_ONLY.getDisplayName();
    public static final String DEFAULT_COMMAND_PREFIX = "!";
    // big Boolean to support backwards compatibility
    private Boolean enabled;
    // the following 2 are actually the Jabber nick and password. For backward compatibility I cannot rename them
    private String hudsonNickname;
    private String hudsonPassword;
    private String groupChatNickname;
    private boolean exposePresence = true;
    private String initialGroupChats;
    private String commandPrefix = DEFAULT_COMMAND_PREFIX;
    private String hudsonCiLogin;
    private String hudsonCiPassword;

    public SkypePublisherDescriptor() {
        super(SkypePublisher.class);
        load();

        if (isEnabled()) {
            try {
                SkypeIMConnectionProvider.setDesc(this);
            } catch (final Exception e) {
                // Server temporarily unavailable or misconfigured?
                LOGGER.warning(ExceptionHelper.dump(e));
            }
        } else {
            try {
                SkypeIMConnectionProvider.setDesc(null);
            } catch (IMException e) {
                // ignore
                LOGGER.info(ExceptionHelper.dump(e));
            }
        }
    }

    @Override
    public void load() {
        super.load();
        if (this.enabled == null) {
            // migrate from plugin < v1.0
            if (Util.fixEmptyAndTrim(this.hudsonNickname) != null) {
                this.enabled = Boolean.TRUE;
            } else {
                this.enabled = Boolean.FALSE;
            }
        }
    }

    private void applyGroupChatNickname(final HttpServletRequest req) throws FormException {
        this.groupChatNickname = req.getParameter(SkypePublisherDescriptor.PARAMETERNAME_GROUP_NICKNAME);
        if (this.groupChatNickname != null && this.groupChatNickname.trim().length() == 0) {
            this.groupChatNickname = null;
        }
    }

    private void applyInitialGroupChats(final HttpServletRequest req) {
        this.initialGroupChats = Util.fixEmptyAndTrim(req.getParameter(SkypePublisherDescriptor.PARAMETERNAME_INITIAL_GROUPCHATS));
    }

    private void applyCommandPrefix(final HttpServletRequest req) {
        String prefix = req.getParameter(SkypePublisherDescriptor.PARAMETERNAME_COMMAND_PREFIX);
        if ((prefix != null) && (prefix.trim().length() > 0)) {
            this.commandPrefix = prefix;
        } else {
            this.commandPrefix = DEFAULT_COMMAND_PREFIX;
        }
    }

    private void applyHudsonLoginPassword(HttpServletRequest req) throws FormException {
        this.hudsonCiLogin = Util.fixEmptyAndTrim(req.getParameter(PARAMETERNAME_HUDSON_LOGIN));
        this.hudsonCiPassword = Util.fixEmptyAndTrim(req.getParameter(PARAMETERNAME_HUDSON_PASSWORD));
        if (this.hudsonCiLogin != null) {
            Authentication auth = new UsernamePasswordAuthenticationToken(this.hudsonCiLogin, this.hudsonCiPassword);
            try {
                Hudson.getInstance().getSecurityRealm().getSecurityComponents().manager.authenticate(auth);
            } catch (AuthenticationException e) {
                throw new FormException(e, "Bad Hudson credentials");
            }
        }
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    @Override
    public String getDisplayName() {
        return "Skype Notification";
    }

    public String getPluginDescription() {
        return "Skype plugin";
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.enabled);
    }

    public String getHostname() {
        throw new UnsupportedOperationException();
    }

    public String getPassword() {
        return this.hudsonPassword;
    }

    public String getGroupChatNickname() {
        return this.groupChatNickname;
    }

    public int getPort() {
        throw new UnsupportedOperationException();
    }

    public boolean isExposePresence() {
        return this.exposePresence;
    }

    /**
     * Gets the whitespace separated list of group chats to join,
     * or null if nothing is configured.
     */
    public String getInitialGroupChats() {
        return Util.fixEmptyAndTrim(this.initialGroupChats);
    }

    public String getDefaultIdSuffix() {
        return null;
    }

    public String getCommandPrefix() {
        return this.commandPrefix;
    }

    /**
     * Creates a new instance of {@link SkypePublisher} from a submitted form.
     */
    @Override
    public SkypePublisher newInstance(final StaplerRequest req, JSONObject formData) throws FormException {
        Assert.notNull(req, "Parameter 'req' must not be null.");
        final String t = req.getParameter(SkypePublisherDescriptor.PARAMETERNAME_TARGETS);
        final String[] split;
        if (t != null) {
            split = t.split("\\s");
        } else {
            split = new String[0];
        }

        List<IMMessageTarget> targets = new ArrayList<IMMessageTarget>(split.length);


        try {
            final IMMessageTargetConverter conv = getIMMessageTargetConverter();
            for (String fragment : split) {
                IMMessageTarget createIMMessageTarget;
                createIMMessageTarget = conv.fromString(fragment);
                if (createIMMessageTarget != null) {
                    targets.add(createIMMessageTarget);
                }
            }
        } catch (IMMessageTargetConversionException e) {
            throw new FormException("Invalid SkypeID", e, SkypePublisherDescriptor.PARAMETERNAME_TARGETS);
        }

        String n = req.getParameter(SkypePublisherDescriptor.PARAMETERNAME_STRATEGY);
        if (n == null) {
            n = PARAMETERVALUE_STRATEGY_DEFAULT;
        } else {
            boolean foundStrategyValueMatch = false;
            for (final String strategyValue : PARAMETERVALUE_STRATEGY_VALUES) {
                if (strategyValue.equals(n)) {
                    foundStrategyValueMatch = true;
                    break;
                }
            }
            if (!foundStrategyValueMatch) {
                n = PARAMETERVALUE_STRATEGY_DEFAULT;
            }
        }
        boolean notifyStart = "on".equals(req.getParameter(getParamNames().getNotifyStart()));
        boolean notifySuspects = "on".equals(req.getParameter(getParamNames().getNotifySuspects()));
        boolean notifyCulprits = "on".equals(req.getParameter(getParamNames().getNotifyCulprits()));
        boolean notifyFixers = "on".equals(req.getParameter(getParamNames().getNotifyFixers()));
        boolean notifyUpstream = "on".equals(req.getParameter(getParamNames().getNotifyUpstreamCommitters()));

        MatrixJobMultiplier matrixJobMultiplier = MatrixJobMultiplier.ONLY_CONFIGURATIONS;
        if (formData.has("matrixNotifier")) {
            String o = formData.getString("matrixNotifier");
            matrixJobMultiplier = MatrixJobMultiplier.valueOf(o);
        }

        return new SkypePublisher(targets, n, notifyStart, notifySuspects, notifyCulprits,
                    notifyFixers, notifyUpstream, req.bindJSON(BuildToChatNotifier.class,formData.getJSONObject("buildToChatNotifier")),
            		matrixJobMultiplier);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
        String en = req.getParameter(PARAMETERNAME_ENABLED);
        this.enabled = Boolean.valueOf(en != null);
        this.exposePresence = req.getParameter(SkypePublisherDescriptor.PARAMETERNAME_PRESENCE) != null;

        applyGroupChatNickname(req);
        applyInitialGroupChats(req);
        applyCommandPrefix(req);
        applyHudsonLoginPassword(req);

        if (isEnabled()) {
            try {
                SkypeIMConnectionProvider.setDesc(this);
                SkypeIMConnectionProvider.getInstance().connectionBroken(null);
            } catch (final Exception e) {
                LOGGER.warning(ExceptionHelper.dump(e));
            }
        } else {
            SkypeIMConnectionProvider.getInstance().releaseConnection();
            try {
                SkypeIMConnectionProvider.setDesc(null);
            } catch (IMException e) {
                // ignore
                LOGGER.info(ExceptionHelper.dump(e));
            }
            LOGGER.info("No hostname specified.");
        }
        save();
        return super.configure(req, json);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("rawtypes")
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return true;
    }

    /**
     *Returns the skypeid.
     */
    public String getUserName() {
        return this.hudsonNickname;
    }

    public String getHudsonUserName() {
        return this.hudsonCiLogin;
    }

    public IMMessageTargetConverter getIMMessageTargetConverter() {
        return SkypePublisher.CONVERTER;
    }

    public List<IMMessageTarget> getDefaultTargets() {
        // not implemented for Skype bot
        return Collections.emptyList();
    }

    public String getHost() {
        throw new UnsupportedOperationException();
    }

    public ParameterNames getParamNames() {
        return new ParameterNames() {

            @Override
            protected String getPrefix() {
                return PREFIX;
            }
        };
    }
}
