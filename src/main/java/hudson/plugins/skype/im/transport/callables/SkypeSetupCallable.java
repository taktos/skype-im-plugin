/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.skype.im.transport.callables;

import hudson.plugins.im.bot.Bot;
import hudson.plugins.skype.im.transport.SkypeChat;
import hudson.plugins.skype.im.transport.SkypeIMException;
import hudson.plugins.skype.im.transport.SkypeMessage;
import hudson.remoting.Callable;
import hudson.remoting.Channel;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.skype.Chat;
import com.skype.ChatMessage;
import com.skype.ChatMessageListener;
import com.skype.Skype;
import com.skype.SkypeException;

/**
 *
 * @author jbh
 */
public class SkypeSetupCallable implements Callable<Boolean, SkypeIMException> {
	private static final Logger LOGGER = Logger.getLogger(SkypeSetupCallable.class.getName());

	private final String[] groupChats;
	private final String botCommandPrefix;

	public SkypeSetupCallable(String[] groupChats, String botCommandPrefix) {
		this.groupChats = groupChats;
		this.botCommandPrefix = botCommandPrefix;
	}

	public Boolean call() throws SkypeIMException {
		try {
			if (!Skype.isInstalled()) {
				throw new RuntimeException("Skype not installed.");
			}
			if (!Skype.isRunning()) {
				LOGGER.warning("Skype is probably not running");
			}
			Skype.setDebug(true);
			Skype.setDaemon(true);
			addSkypeListener(Channel.current());
			return true;
		} catch (SkypeException ex) {
			throw new SkypeIMException(ex);
		}
	}

	private void addSkypeListener(Channel channel) throws SkypeException {
		final IMListener listener = new SkypeSetupCallable.IMListener(channel);
		Skype.addChatMessageListener(listener);
		if (channel != null) {
			channel.addListener(new Channel.Listener() {
				@Override
				public void onClosed(Channel channel, IOException cause) {
					Skype.removeChatMessageListener(listener);
					LOGGER.info("Removed skype listener");
				}
			});
		}
	}

	private final class IMListener implements ChatMessageListener {

		Channel masterChannel = null;

		public IMListener(Channel channel) {
			masterChannel = channel;
		}

		public void chatMessageReceived(ChatMessage receivedChatMessage) throws SkypeException {
			if (receivedChatMessage.getType().equals(ChatMessage.Type.SAID) && isValidGroup(receivedChatMessage)) {
				LOGGER.fine("Message from " + receivedChatMessage.getSenderDisplayName() + " : " + receivedChatMessage.getContent());

				final String chatPartner = receivedChatMessage.getSenderId();
				getChat(chatPartner, receivedChatMessage);
			}
		}

		public void chatMessageSent(ChatMessage sentChatMessage) throws SkypeException {
		}

		private boolean isValidGroup(ChatMessage receivedChatMessage) throws SkypeException {
			return groupChats.length == 0 || Arrays.asList(groupChats).contains(receivedChatMessage.getChat().getWindowTitle());
        }

		private void getChat(String chatPartner, ChatMessage receivedChatMessage) {
			final Chat chat;
			try {
				chat = receivedChatMessage.getChat();
				if (masterChannel != null) {
					masterChannel.call(new BotCommandCallable(chat, receivedChatMessage, botCommandPrefix));
				} else {
					SkypeChat skypeChat = new SkypeChat(chat);
					Bot bot = new Bot(skypeChat, "hudson", "hostname", botCommandPrefix, null);
					if (receivedChatMessage != null) {
						// replay original message:
						bot.onMessage(new SkypeMessage(receivedChatMessage, true));// Ask skype
					}
				}
			} catch (SkypeException ex) {
				LOGGER.log(Level.SEVERE, null, ex);
			} catch (Exception ex) {
				LOGGER.log(Level.SEVERE, null, ex);
				throw new RuntimeException(ex);
			}
		}
	};
}
