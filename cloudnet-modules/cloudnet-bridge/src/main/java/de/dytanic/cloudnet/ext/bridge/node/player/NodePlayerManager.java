package de.dytanic.cloudnet.ext.bridge.node.player;

import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.common.Validate;
import de.dytanic.cloudnet.common.collection.Iterables;
import de.dytanic.cloudnet.common.collection.Maps;
import de.dytanic.cloudnet.common.concurrent.ITask;
import de.dytanic.cloudnet.common.concurrent.ListenableTask;
import de.dytanic.cloudnet.common.document.gson.JsonDocument;
import de.dytanic.cloudnet.database.IDatabase;
import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.driver.service.ServiceEnvironmentType;
import de.dytanic.cloudnet.ext.bridge.BridgeConstants;
import de.dytanic.cloudnet.ext.bridge.player.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class NodePlayerManager implements IPlayerManager {

    private static NodePlayerManager instance;

    private final Map<UUID, CloudPlayer> onlineCloudPlayers = Maps.newConcurrentHashMap();

    private final String databaseName;

    public NodePlayerManager(String databaseName) {
        this.databaseName = databaseName;


        instance = this;
    }

    public static NodePlayerManager getInstance() {
        return NodePlayerManager.instance;
    }


    public IDatabase getDatabase() {
        return CloudNet.getInstance().getDatabaseProvider().getDatabase(databaseName);
    }


    @Override
    public int getOnlineCount() {
        return this.onlineCloudPlayers.size();
    }

    @Override
    public int getRegisteredCount() {
        return this.getDatabase().getDocumentsCount();
    }

    @Override
    public CloudPlayer getOnlinePlayer(UUID uniqueId) {
        return onlineCloudPlayers.get(uniqueId);
    }

    @Override
    public List<? extends ICloudPlayer> getOnlinePlayers(String name) {
        Validate.checkNotNull(name);

        return Iterables.filter(this.onlineCloudPlayers.values(), cloudPlayer -> cloudPlayer.getName().equalsIgnoreCase(name));
    }

    @Override
    public List<? extends ICloudPlayer> getOnlinePlayers(ServiceEnvironmentType environment) {
        Validate.checkNotNull(environment);

        return Iterables.filter(this.onlineCloudPlayers.values(), cloudPlayer -> (cloudPlayer.getLoginService() != null && cloudPlayer.getLoginService().getEnvironment() == environment) ||
                (cloudPlayer.getConnectedService() != null && cloudPlayer.getConnectedService().getEnvironment() == environment));
    }

    @Override
    public List<? extends ICloudPlayer> getOnlinePlayers() {
        return Iterables.newArrayList(this.onlineCloudPlayers.values());
    }

    @Override
    public void requestOnlinePlayers(Consumer<ICloudPlayer> playerAcceptor) {
        try {
            this.requestOnlinePlayersAsync(playerAcceptor).get();
        } catch (InterruptedException | ExecutionException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public ICloudOfflinePlayer getOfflinePlayer(UUID uniqueId) {
        Validate.checkNotNull(uniqueId);

        JsonDocument jsonDocument = getDatabase().get(uniqueId.toString());

        return jsonDocument != null ? jsonDocument.toInstanceOf(CloudOfflinePlayer.TYPE) : null;
    }

    @Override
    public List<? extends ICloudOfflinePlayer> getOfflinePlayers(String name) {
        Validate.checkNotNull(name);

        return Iterables.map(getDatabase().get(new JsonDocument("name", name)), jsonDocument -> jsonDocument.toInstanceOf(CloudOfflinePlayer.TYPE));
    }

    @Override
    public void requestRegisteredPlayers(Consumer<ICloudOfflinePlayer> playerAcceptor) {
        getDatabase().iterate((s, jsonDocument) -> playerAcceptor.accept(jsonDocument.toInstanceOf(CloudOfflinePlayer.TYPE)));
    }

    @Override
    public ITask<Integer> getOnlineCountAsync() {
        return this.schedule(this::getOnlineCount);
    }

    @Override
    public ITask<Integer> getRegisteredCountAsync() {
        return this.getDatabase().getDocumentsCountAsync();
    }

    @Override
    public ITask<ICloudPlayer> getOnlinePlayerAsync(UUID uniqueId) {
        return this.schedule(() -> this.getOnlinePlayer(uniqueId));
    }

    @Override
    public ITask<List<? extends ICloudPlayer>> getOnlinePlayersAsync(String name) {
        return this.schedule(() -> this.getOnlinePlayers(name));
    }

    @Override
    public ITask<List<? extends ICloudPlayer>> getOnlinePlayersAsync(ServiceEnvironmentType environment) {
        return this.schedule(() -> this.getOnlinePlayers(environment));
    }

    @Override
    public ITask<List<? extends ICloudPlayer>> getOnlinePlayersAsync() {
        return this.schedule(this::getOnlinePlayers);
    }

    @Override
    public ITask<Void> requestOnlinePlayersAsync(Consumer<ICloudPlayer> playerAcceptor) {
        return this.schedule(() -> {
            this.getDatabase().iterate((s, jsonDocument) -> playerAcceptor.accept(jsonDocument.toInstanceOf(CloudPlayer.TYPE)));
            return null;
        });
    }

    @Override
    public ITask<ICloudOfflinePlayer> getOfflinePlayerAsync(UUID uniqueId) {
        return this.schedule(() -> this.getOfflinePlayer(uniqueId));
    }

    @Override
    public ITask<List<? extends ICloudOfflinePlayer>> getOfflinePlayersAsync(String name) {
        return this.schedule(() -> this.getOfflinePlayers(name));
    }

    @Override
    public ITask<List<? extends ICloudOfflinePlayer>> getRegisteredPlayersAsync() {
        return this.schedule(this::getRegisteredPlayers);
    }

    public List<ICloudOfflinePlayer> getRegisteredPlayersInRange(int from, int to) {
        return this.getDatabase().documentsInRange(from, to)
                .stream()
                .map(document -> (ICloudOfflinePlayer) document.toInstanceOf(CloudOfflinePlayer.TYPE))
                .collect(Collectors.toList());
    }

    @Override
    public ITask<Void> requestRegisteredPlayersAsync(Consumer<ICloudOfflinePlayer> playerAcceptor) {
        return null;
    }


    @Override
    public void updateOfflinePlayer(ICloudOfflinePlayer cloudOfflinePlayer) {
        Validate.checkNotNull(cloudOfflinePlayer);

        updateOfflinePlayer0(cloudOfflinePlayer);
        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "update_offline_cloud_player",
                new JsonDocument(
                        "offlineCloudPlayer", cloudOfflinePlayer
                )
        );
    }

    public void updateOfflinePlayer0(ICloudOfflinePlayer cloudOfflinePlayer) {
        getDatabase().update(cloudOfflinePlayer.getUniqueId().toString(), JsonDocument.newDocument(cloudOfflinePlayer));
    }

    @Override
    public void updateOnlinePlayer(ICloudPlayer cloudPlayer) {
        Validate.checkNotNull(cloudPlayer);

        updateOnlinePlayer0(cloudPlayer);
        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "update_online_cloud_player",
                new JsonDocument(
                        "cloudPlayer", cloudPlayer
                )
        );
    }

    public void updateOnlinePlayer0(ICloudPlayer cloudPlayer) {
        updateOfflinePlayer0(CloudOfflinePlayer.of(cloudPlayer));
    }

    @Override
    public void proxySendPlayer(ICloudPlayer cloudPlayer, String serviceName) {
        Validate.checkNotNull(cloudPlayer);
        Validate.checkNotNull(serviceName);

        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "send_on_proxy_player_to_server",
                new JsonDocument()
                        .append("uniqueId", cloudPlayer.getUniqueId())
                        .append("serviceName", serviceName)
        );
    }

    @Override
    public void proxySendPlayerMessage(ICloudPlayer cloudPlayer, String message) {
        Validate.checkNotNull(cloudPlayer);
        Validate.checkNotNull(message);

        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "send_message_to_proxy_player",
                new JsonDocument()
                        .append("uniqueId", cloudPlayer.getUniqueId())
                        .append("name", cloudPlayer.getName())
                        .append("message", message)
        );
    }

    @Override
    public void proxyKickPlayer(ICloudPlayer cloudPlayer, String kickMessage) {
        Validate.checkNotNull(cloudPlayer);
        Validate.checkNotNull(kickMessage);

        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "kick_on_proxy_player_from_network",
                new JsonDocument()
                        .append("uniqueId", cloudPlayer.getUniqueId())
                        .append("name", cloudPlayer.getName())
                        .append("kickMessage", kickMessage)
        );
    }

    @Override
    public void broadcastMessage(String message) {
        Validate.checkNotNull(message);

        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "broadcast_message",
                new JsonDocument()
                        .append("message", message)
        );
    }

    @Override
    public void broadcastMessage(String message, String permission) {
        Validate.checkNotNull(message);
        Validate.checkNotNull(permission);

        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "broadcast_message",
                new JsonDocument()
                        .append("message", message)
                        .append("permission", permission)
        );
    }


    private <T> ITask<T> schedule(Callable<T> callable) {
        return CloudNet.getInstance().getTaskScheduler().schedule(callable);
    }

    public Map<UUID, CloudPlayer> getOnlineCloudPlayers() {
        return this.onlineCloudPlayers;
    }

    public String getDatabaseName() {
        return this.databaseName;
    }
}