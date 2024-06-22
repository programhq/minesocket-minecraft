package gg.program.minesocket;

import java.util.HashMap;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.CommandException;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.io.File;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

public class MineSocket extends JavaPlugin {
  private WebSocketServer server;
  private HashMap<String, Boolean> authorized = new HashMap<>();

  private boolean configExists() {
    String configFilePath = String.format("%s/config.yml", getDataFolder().getAbsolutePath());
    return new File(configFilePath).exists();
  }

  @Override
  public void onEnable() {
    if (!configExists()) saveDefaultConfig();

    int port = getConfig().getInt("port");
    String password = getConfig().getString("password");

    InputStreamReader defaultConfig = new InputStreamReader(getResource("config.yml"));
    String defaultPassword = YamlConfiguration.loadConfiguration(defaultConfig).getString("password");

    if (port == 0) {
      getLogger().severe("A port must be specified to start the WebSocket Server!");
      return;
    }

    if (password.isBlank()) {
      getLogger().warning("The WebSocket Server is unprotected; it's recommended to set a password!");
    }

    if (password.equals(defaultPassword)) {
      getLogger().warning("The WebSocket Server is using the default password; it's recommended to change it!");
    }

    MineSocket plugin = this;
    ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();

    getLogger().info("[WebSocket Server - Starting]");

    server = new WebSocketServer(new InetSocketAddress(port)) {
      @Override
      public void onOpen(WebSocket conn, ClientHandshake handshake) {
        getLogger().info(String.format("[WebSocket Server - Connected] %s", conn.getRemoteSocketAddress()));
      }

      @Override
      public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String client = conn.getRemoteSocketAddress().toString();

        getLogger().info(String.format("[WebSocket Server - Disconnected] %s", client));

        authorized.remove(client);
      }

      @Override
      public void onMessage(WebSocket conn, String message) {
        String client = conn.getRemoteSocketAddress().toString();

        if (message.startsWith("auth:")) {
          if (password.isBlank()) {
            conn.send("{ \"authorized\": true }");
            return;
          }

          getLogger().info(String.format("[WebSocket Server - Authorization Pending] %s", client));

          try {
            String messagePassword = message.split(":")[1];
            if (messagePassword.equals(password)) {
              authorized.put(client, true);


              getLogger().info(String.format("[WebSocket Server - Authorization Success] %s", client));

              conn.send("{ \"authorized\": true }");
            } else {
              throw new Exception("Invalid password");
            }
          } catch (Exception e) {
            authorized.put(client, false);

            getLogger().info(String.format("[WebSocket Server - Authorization Failure] %s", client));

            conn.send("{ \"authorized\": false }");
            conn.close(1003, e.getMessage());
          }

          return;
        }

        getLogger().info(String.format("[WebSocket Server - Message] %s: %s", client, message));

        if (!password.isBlank() && !authorized.get(client)) {
          authorized.put(client, false);

          conn.send("{ \"authorized\": false }");
          conn.close(1003, "Invalid password");

          return;
        }

        Bukkit.getScheduler().runTask(plugin, new Runnable() {
          @Override
          public void run() {
            try {
              Bukkit.dispatchCommand(console, message);
              conn.send("{ \"success\": true }");
            } catch (CommandException ex) {
              conn.send("{ \"success\": false }");
            }
          }
        });
      }

      @Override
      public void onError(WebSocket conn, Exception ex) {
        getLogger().info(String.format("[WebSocket Server - Error] %s: %s", conn.getRemoteSocketAddress(), ex.getMessage()));
      }

      @Override
      public void onStart() {
        getLogger().info(String.format("[WebSocket Server - Started] Port %s", port));
      }
    };
    server.start();
  }

  @Override
  public void onDisable() {
    getLogger().info("[WebSocket Server - Stopping]");

    if (server != null) {
      try {
        server.stop();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
