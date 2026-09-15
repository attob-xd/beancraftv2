package net.lax1dude.eaglercraft.sp.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.nbt.CompoundTag;

/**
 * Copyright (c) 2023-2024 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class EaglerPlayerList extends PlayerList {

	private CompoundTag hostPlayerNBT = null;

	public EaglerPlayerList(MinecraftServer par1MinecraftServer, int viewDistance) {
		// 1.18.2's PlayerList takes the registries, the player-data store and the view
		// distance up front; 1.12.2 took only the server and let you set the rest after.
		super(par1MinecraftServer, par1MinecraftServer.registryAccess(),
				((net.lax1dude.eaglercraft.sp.server.EaglerMinecraftServer) par1MinecraftServer).getPlayerDataStorage(), viewDistance);
	}

	protected void save(ServerPlayer par1EntityPlayerMP) {
		if (par1EntityPlayerMP.getName().equals(this.getServer().getSingleplayerName())) {
			this.hostPlayerNBT = new CompoundTag();
			par1EntityPlayerMP.save(hostPlayerNBT);
		}
		super.save(par1EntityPlayerMP);
	}

	public CompoundTag getHostPlayerData() {
		return this.hostPlayerNBT;
	}

	/**
	 * 1.12.2's hook was {@code playerLoggedOut}; 1.18.2 renamed it to {@code remove}, so the
	 * ported method overrode nothing and vanilla never called it - the skin service kept every
	 * player it had ever seen. Guarded on the server type because {@code remove} also runs
	 * during shutdown, where the old name never did.
	 */
	@Override
	public void remove(ServerPlayer playerIn) {
		super.remove(playerIn);
		if (getServer() instanceof EaglerMinecraftServer) {
			((EaglerMinecraftServer) getServer()).skinService
					.unregisterPlayer(net.lax1dude.eaglercraft.EaglercraftUUID.fromJavaUUID(playerIn.getUUID()));
		}
	}

	/**
	 * Gives this player's connection the server half of EaglercraftX's plugin-message
	 * protocol.
	 *
	 * <p>The client builds its controller as it logs in, and {@link EaglerServerState} was
	 * ready to hold the server's - but nothing ever constructed one, so
	 * {@code sendEaglerMessage} was a no-op on the server and every skin or cape the client
	 * asked for went unanswered. This is where the server first has both halves it needs: the
	 * transport, which carries the agreed protocol version, and the play listener the
	 * controller has to send through.
	 *
	 * <p>A connection that is not one of EaglercraftX's transports, or that reports a version
	 * this build does not implement, is left alone: a vanilla client is a perfectly ordinary
	 * thing for this server to be talking to, and it simply never receives these messages.
	 */
	@Override
	public void placeNewPlayer(net.minecraft.network.Connection connection, ServerPlayer player) {
		super.placeNewPlayer(connection, player);
		if (!(connection instanceof net.lax1dude.eaglercraft.compat.EaglerNetworkManager)) {
			return;
		}
		int ver = ((net.lax1dude.eaglercraft.compat.EaglerNetworkManager) connection).getEaglerProtocolVersion();
		net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageProtocol protocol =
				net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageProtocol.getByVersion(ver);
		if (protocol == null) {
			return;
		}
		net.minecraft.server.network.ServerGamePacketListenerImpl listener = player.connection;
		net.lax1dude.eaglercraft.compat.EaglerServerState.get(listener).setEaglerMessageController(
				new net.lax1dude.eaglercraft.socket.protocol.client.GameProtocolMessageController(
						protocol,
						net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageConstants.SERVER_TO_CLIENT,
						net.lax1dude.eaglercraft.socket.protocol.client.GameProtocolMessageController
								.createServerHandler(ver, listener),
						(ch, msg) -> {
							// EaglercraftX's channel names are not legal ResourceLocations;
							// see EaglerPluginChannels.
							net.minecraft.resources.ResourceLocation chId =
									net.lax1dude.eaglercraft.compat.EaglerPluginChannels.toId(ch);
							if (chId == null) {
								return;
							}
							listener.send(new net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket(
									chId, msg));
						}));
	}
}