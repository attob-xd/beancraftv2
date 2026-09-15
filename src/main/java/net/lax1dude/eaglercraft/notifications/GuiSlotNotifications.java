package net.lax1dude.eaglercraft.notifications;

import net.lax1dude.eaglercraft.compat.EaglerGuiCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.lax1dude.eaglercraft.socket.protocol.pkt.server.SPacketNotifBadgeShowV4EAG.EnumBadgePriority;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;

/**
 * Copyright (c) 2024 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class GuiSlotNotifications extends GuiSlot {

	private static final ResourceLocation eaglerGui = new ResourceLocation("eagler:gui/eagler_gui.png");
	private static final ResourceLocation largeNotifBk = new ResourceLocation("eagler:gui/notif_bk_large.png");

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm a");

	final GuiScreenNotifications parent;
	final List<NotifBadgeSlot> currentDisplayNotifs;

	int mouseX;
	int mouseY;

	protected static class NotifBadgeSlot {
		
		protected final NotificationBadge badge;
		protected final List<ClickEventZone> cursorEvents = new ArrayList<>();
		protected int currentScreenX = -69420;
		protected int currentScreenY = -69420;
		
		protected NotifBadgeSlot(NotificationBadge badge) {
			this.badge = badge;
		}
		
	}

	public GuiSlotNotifications(GuiScreenNotifications parent) {
		super(GuiScreenNotifications.getMinecraft(parent), parent.width, parent.height, 32, parent.height - 44, 68);
		this.parent = parent;
		this.currentDisplayNotifs = new ArrayList<>();
	}

	@Override
	protected int getSize() {
		return currentDisplayNotifs.size();
	}

	@Override
	protected void elementClicked(int id, boolean doubleClk, int xx, int yy) {
		if(selectedElement != id) return; //workaround for vanilla bs
		if(id < currentDisplayNotifs.size()) {
			NotifBadgeSlot slot = currentDisplayNotifs.get(id);
			if(slot.currentScreenY != -69420) {
				int w = getListWidth();
				int localX = xx - slot.currentScreenX;
				int localY = yy - slot.currentScreenY;
				if(localX >= w - 22 && localX < w - 5 && localY >= 5 && localY < 21) {
					slot.badge.removeNotif();
					mc.getSoundManager().play(net.lax1dude.eaglercraft.compat.EaglerSoundCompat.forUI(new ResourceLocation("gui.button.press"), 1.0F));
					return;
				}
				Component cmp = slot.badge.bodyComponent;
				if(cmp != null) {
					if(doubleClk) {
						if (cmp.getStyle().getClickEvent() != null
								&& cmp.getStyle().getClickEvent().getAction().isAllowedFromServer()) {
							if(parent.handleComponentClicked(cmp.getStyle())) {
								mc.getSoundManager().play(net.lax1dude.eaglercraft.compat.EaglerSoundCompat.forUI(new ResourceLocation("gui.button.press"), 1.0F));
								return;
							}
						}
					}else {
						if(parent.selected != id) {
							parent.selected = id;
						}else {
							List<ClickEventZone> cursorEvents = slot.cursorEvents;
							if(cursorEvents != null && !cursorEvents.isEmpty()) {
								for(int j = 0, m = cursorEvents.size(); j < m; ++j) {
									ClickEventZone evt = cursorEvents.get(j);
									if(evt.hasClickEvent) {
										int offsetPosX = slot.currentScreenX + evt.posX;
										int offsetPosY = slot.currentScreenY + evt.posY;
										if(xx >= offsetPosX && yy >= offsetPosY && xx < offsetPosX + evt.width && yy < offsetPosY + evt.height) {
											if(parent.handleComponentClicked(evt.chatComponent.getStyle())) {
												mc.getSoundManager().play(net.lax1dude.eaglercraft.compat.EaglerSoundCompat.forUI(new ResourceLocation("gui.button.press"), 1.0F));
												return;
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	@Override
	protected boolean isSelected(int var1) {
		return var1 == parent.selected;
	}

	@Override
	protected void drawBackground() {
		parent.drawBackground(0);
	}

	@Override
	protected void func_192637_a(int id, int xx, int yy, int width, int height, int ii, float p_192637_7_) {
		if(id < currentDisplayNotifs.size()) {
			NotifBadgeSlot slot = currentDisplayNotifs.get(id);
			slot.currentScreenX = xx;
			slot.currentScreenY = yy;
			NotificationBadge bd = slot.badge;
			if(yy + 32 > this.top && yy + 32 < this.bottom) {
				bd.markRead();
			}
			EaglerGuiCompat.currentPoseStack().pushPose();
			EaglerGuiCompat.currentPoseStack().translate(xx, yy, 0.0f);
			EaglerGuiCompat.bindGuiTexture(largeNotifBk);
			int badgeWidth = getListWidth() - 4;
			int badgeHeight = getSlotHeight() - 4;
			float r = ((bd.backgroundColor >> 16) & 0xFF) * 0.00392156f;
			float g = ((bd.backgroundColor >> 8) & 0xFF) * 0.00392156f;
			float b = (bd.backgroundColor & 0xFF) * 0.00392156f;
			if(parent.selected != id) {
				r *= 0.85f;
				g *= 0.85f;
				b *= 0.85f;
			}
			GlStateManager.color(r, g, b, 1.0f);
			parent.drawTexturedModalRect(0, 0, 0, bd.unreadFlagRender ? 64 : 0, badgeWidth - 32, 64);
			parent.drawTexturedModalRect(badgeWidth - 32, 0, 224, bd.unreadFlagRender ? 64 : 0, 32, 64);
			EaglerGuiCompat.bindGuiTexture(eaglerGui);
			if(bd.priority == EnumBadgePriority.LOW) {
				parent.drawTexturedModalRect(badgeWidth - 21, badgeHeight - 21, 192, 176, 16, 16);
			}
			GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
			
			switch(bd.priority) {
			default:
				break;
			case NORMAL:
				parent.drawTexturedModalRect(badgeWidth - 21, badgeHeight - 21, 208, 176, 16, 16);
				break;
			case HIGHER:
				parent.drawTexturedModalRect(badgeWidth - 21, badgeHeight - 21, 224, 176, 16, 16);
				break;
			case HIGHEST:
				parent.drawTexturedModalRect(badgeWidth - 21, badgeHeight - 21, 240, 176, 16, 16);
				break;
			}
			
			int bodyYOffset = 16;
					
			int leftPadding = 6;
			int rightPadding = 26;
			
			int mainIconSW = 32;
			boolean mainIconEn = bd.mainIcon != null && bd.mainIcon.isValid();
			if(mainIconEn) {
				int iw = bd.mainIcon.texture.getWidth();
				int ih = bd.mainIcon.texture.getHeight();
				float iaspect = (float)iw / (float)ih;
				mainIconSW = (int)(32 * iaspect);
				leftPadding += Math.min(mainIconSW, 64) + 3;
			}
			
			int textZoneWidth = badgeWidth - leftPadding - rightPadding;
			
			if(mainIconEn) {
				EaglerGuiCompat.bindGuiTexture(bd.mainIcon.resource);
				ServerNotificationRenderer.drawTexturedRect(6, bodyYOffset, mainIconSW, 32);
			}

			boolean titleIconEn = bd.titleIcon != null && bd.titleIcon.isValid();
			if(titleIconEn) {
				EaglerGuiCompat.bindGuiTexture(bd.titleIcon.resource);
				ServerNotificationRenderer.drawTexturedRect(6, 5, 8, 8);
			}
			
			String titleText = "";
			Component titleComponent = bd.getTitleProfanityFilter();
			if(titleComponent != null) {
				titleText = titleComponent.getString();
			}
			
			titleText += ChatFormatting.GRAY + (titleText.length() > 0 ? " @ " : "@ ")
					+ (bd.unreadFlagRender ? ChatFormatting.YELLOW : ChatFormatting.GRAY)
					+ formatAge(bd.serverTimestamp);

			EaglerGuiCompat.currentPoseStack().pushPose();
			EaglerGuiCompat.currentPoseStack().translate(6 + (titleIconEn ? 10 : 0), 6, 0.0f);
			EaglerGuiCompat.currentPoseStack().scale(0.75f, 0.75f, 0.75f);
			mc.font.drawShadow(net.lax1dude.eaglercraft.compat.EaglerGuiCompat.currentPoseStack(), titleText, 0, 0, bd.titleTxtColor);
			EaglerGuiCompat.currentPoseStack().popPose();
			
			String sourceText = null;
			Component sourceComponent = bd.getSourceProfanityFilter();
			if(sourceComponent != null) {
				sourceText = sourceComponent.getString();
				if(sourceText.length() == 0) {
					sourceText = null;
				}
			}
			
			List<net.minecraft.util.FormattedCharSequence> bodyLines = null;
			float bodyFontSize = (sourceText != null || titleIconEn) ? 0.75f : 1.0f;
			Component bodyComponent = bd.getBodyProfanityFilter();
			if(bodyComponent != null) {
				bodyLines = ComponentRenderUtils.wrapComponents(bodyComponent, (int) (textZoneWidth / bodyFontSize), mc.font);
				
				int maxHeight = badgeHeight - (sourceText != null ? 32 : 22);
				int maxLines = Mth.floor(maxHeight / (9 * bodyFontSize));
				if(bodyLines.size() > maxLines) {
					bodyLines = bodyLines.subList(0, maxLines);
					// 1.12.2 appended "..." into the last wrapped Component. A
					// FormattedCharSequence cannot be appended to, so the ellipsis is
					// composed onto the line as a second sequence instead.
					bodyLines = new java.util.ArrayList<>(bodyLines);
					bodyLines.set(maxLines - 1, net.minecraft.util.FormattedCharSequence.composite(
							bodyLines.get(maxLines - 1),
							net.minecraft.util.FormattedCharSequence.forward("...",
									net.minecraft.network.chat.Style.EMPTY)));
				}
			}
			
			slot.cursorEvents.clear();
			if(bodyLines != null && !bodyLines.isEmpty()) {
				EaglerGuiCompat.currentPoseStack().pushPose();
				EaglerGuiCompat.currentPoseStack().translate(leftPadding, bodyYOffset, 0.0f);
				int l = bodyLines.size();
				EaglerGuiCompat.currentPoseStack().scale(bodyFontSize, bodyFontSize, bodyFontSize);
				Component toolTip = null;
			for(int i = 0; i < l; ++i) {
				// 1.12.2 walked a line's sibling Components to place one click zone per span.
				// 1.18.2 wraps to FormattedCharSequence, which has no components to walk, so the
				// line is drawn in one call and the whole line becomes the zone; the style under
				// the cursor is resolved at click time via the font splitter.
				net.minecraft.util.FormattedCharSequence line = bodyLines.get(i);
				int w = mc.font.drawShadow(
						net.lax1dude.eaglercraft.compat.EaglerGuiCompat.currentPoseStack(), line, 0,
						i * 9, bd.bodyTxtColor);
				net.minecraft.network.chat.Style style =
						mc.font.getSplitter().componentStyleAtWidth(bodyComponent, w);
				if(style != null) {
					ClickEvent clickEvent = style.getClickEvent();
					HoverEvent hoverEvent = style.getHoverEvent();
					if(clickEvent != null && !clickEvent.getAction().isAllowedFromServer()) {
						clickEvent = null;
					}
					if(hoverEvent != null && !hoverEvent.getAction().isAllowedFromServer()) {
						hoverEvent = null;
					}
					if(clickEvent != null || hoverEvent != null) {
						slot.cursorEvents.add(new ClickEventZone(leftPadding,
								bodyYOffset + (int) (i * 9 * bodyFontSize), (int) (w * bodyFontSize),
								(int) (9 * bodyFontSize), bodyComponent, clickEvent != null,
								hoverEvent != null));
						if(hoverEvent != null && toolTip == null) {
							toolTip = bodyComponent;
						}
					}
				}
			}
				EaglerGuiCompat.currentPoseStack().popPose();
				if(toolTip != null) {
					parent.renderComponentHover(toolTip.getStyle(), mouseX - xx, mouseY - yy);
				}
			}
			
			if(sourceText != null) {
				EaglerGuiCompat.currentPoseStack().pushPose();
				EaglerGuiCompat.currentPoseStack().translate(badgeWidth - 21, badgeHeight - 5, 0.0f);
				EaglerGuiCompat.currentPoseStack().scale(0.75f, 0.75f, 0.75f);
				mc.font.drawShadow(net.lax1dude.eaglercraft.compat.EaglerGuiCompat.currentPoseStack(), sourceText, -mc.font.width(sourceText) - 4, -10, bd.sourceTxtColor);
				EaglerGuiCompat.currentPoseStack().popPose();
			}
			
			EaglerGuiCompat.currentPoseStack().popPose();
		}
	}

	private String formatAge(long serverTimestamp) {
		long cur = System.currentTimeMillis();
		long daysAgo = Math.round((cur - serverTimestamp) / 86400000.0);
		String ret = dateFormat.format(new Date(serverTimestamp));
		if(daysAgo > 0l) {
			ret += " (" + daysAgo + (daysAgo == 1l ? " day" : " days") + " ago)";
		}else if(daysAgo < 0l) {
			ret += " (in " + -daysAgo + (daysAgo == -1l ? " day" : " days") + ")";
		}
		return ret;
	}

	@Override
	public int getListWidth() {
		return 224;
	}

	@Override
	public void drawScreen(int mouseXIn, int mouseYIn, float parFloat1) {
		mouseX = mouseXIn;
		mouseY = mouseYIn;
		for(int i = 0, l = currentDisplayNotifs.size(); i < l; ++i) {
			NotifBadgeSlot slot = currentDisplayNotifs.get(i);
			slot.currentScreenX = -69420;
			slot.currentScreenY = -69420;
		}
		super.drawScreen(mouseXIn, mouseYIn, parFloat1);
	}
}
