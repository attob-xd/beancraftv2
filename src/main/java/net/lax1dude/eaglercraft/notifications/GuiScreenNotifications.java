package net.lax1dude.eaglercraft.notifications;

import java.io.IOException;
import java.util.List;
import net.lax1dude.eaglercraft.compat.EaglerClientState;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

import net.lax1dude.eaglercraft.EaglercraftUUID;
import net.lax1dude.eaglercraft.socket.protocol.pkt.server.SPacketNotifBadgeShowV4EAG.EnumBadgePriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.lax1dude.eaglercraft.compat.GuiScreenCompat;
import net.lax1dude.eaglercraft.compat.GuiButtonCompat;
import net.minecraft.client.gui.screens.Screen;

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
public class GuiScreenNotifications extends GuiScreenCompat {

	private static final String[] priorityLangKeys = new String[] {
			"notifications.priority.low",
			"notifications.priority.normal",
			"notifications.priority.higher",
			"notifications.priority.highest"
	};

	private static final int[] priorityOrder = new int[] {
			0, 3, 2, 1
	};

	Screen parent;
	int selected;
	GuiSlotNotifications slots;
	GuiButtonCompat clearAllButton;
	GuiButtonCompat priorityButton;
	int showPriority = 0;
	EnumBadgePriority selectedMaxPriority = EnumBadgePriority.LOW;
	int lastUpdate = -1;

	public GuiScreenNotifications(Screen parent) {
		this.parent = parent;
	}

	public void initGui() {
		selected = -1;
		buttonList.clear();
		buttonList.add(new GuiButtonCompat(0, this.width / 2 + 54, this.height - 32, 100, 20, I18n.get("gui.done")));
		buttonList.add(clearAllButton = new GuiButtonCompat(1, this.width / 2 - 154, this.height - 32, 100, 20,
				I18n.get("notifications.clearAll")));
		int i = priorityOrder[showPriority];
		buttonList.add(priorityButton = new GuiButtonCompat(2, this.width / 2 - 50, this.height - 32, 100, 20,
				I18n.get("notifications.priority", I18n.get(priorityLangKeys[i]))));
		selectedMaxPriority = EnumBadgePriority.getByID(i);
		slots = new GuiSlotNotifications(this);
		lastUpdate = -69420;
		updateList();
		updateButtons();
	}

	void updateButtons() {
		clearAllButton.active = !slots.currentDisplayNotifs.isEmpty();
	}

	void updateList() {
		if(minecraft.player == null) return;
		ServerNotificationManager mgr = EaglerClientState.get(minecraft.player.connection).getNotifManager();
		int verHash = showPriority | (mgr.getNotifListUpdateCount() << 2);
		if(verHash != lastUpdate) {
			lastUpdate = verHash;
			EaglercraftUUID selectedUUID = null;
			List<GuiSlotNotifications.NotifBadgeSlot> lst = slots.currentDisplayNotifs;
			int oldSelectedId = selected;
			if(oldSelectedId >= 0 && oldSelectedId < lst.size()) {
				selectedUUID = lst.get(oldSelectedId).badge.badgeUUID;
			}
			lst.clear();
			mgr.getNotifLongHistory().stream().filter((input) -> input.priority.priority >= priorityOrder[showPriority])
			.map(GuiSlotNotifications.NotifBadgeSlot::new).forEach(lst::add);
			selected = -1;
			if(selectedUUID != null) {
				for(int i = 0, l = lst.size(); i < l; ++i) {
					if(selectedUUID.equals(lst.get(i).badge.badgeUUID)) {
						selected = i;
						break;
					}
				}
			}
			if(selected != -1) {
				if(oldSelectedId != selected) {
					slots.scrollBy((selected - oldSelectedId) * slots.getSlotHeight());
				}
			}
			updateButtons();
		}
	}

	public void updateScreen() {
		if(minecraft.player == null) {
			minecraft.setScreen(parent);
			return;
		}
		updateList();
	}

	static Minecraft getMinecraft(GuiScreenNotifications screen) {
		return net.minecraft.client.Minecraft.getInstance();
	}

	public void actionPerformed(GuiButtonCompat btn) {
		switch(btn.id) {
		case 0:
			minecraft.setScreen(parent);
			break;
		case 1:
			if(minecraft.player != null) {
				ServerNotificationManager mgr = EaglerClientState.get(minecraft.player.connection).getNotifManager();
				mgr.removeAllNotifFromActiveList(mgr.getNotifLongHistory());
				clearAllButton.active = false;
			}
			break;
		case 2:
			showPriority = (showPriority + 1) & 3;
			int i = priorityOrder[showPriority];
			priorityButton.setDisplayString(I18n.get("notifications.priority", I18n.get(priorityLangKeys[i])));
			selectedMaxPriority = EnumBadgePriority.getByID(i);
			updateList();
			break;
		default:
			break;
		}
	}

	public void drawScreen(int par1, int par2, float par3) {
		if(minecraft.player == null) return;
		slots.drawScreen(par1, par2, par3);
		this.drawCenteredString(font, I18n.get("notifications.title"), this.width / 2, 16, 16777215);
		super.drawScreen(par1, par2, par3);
	}

	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		slots.handleMouseInput();
	}

	public void onGuiClosed() {
		if(minecraft.player != null) {
			EaglerClientState.get(minecraft.player.connection).getNotifManager().commitUnreadFlag();
		}
	}
}
