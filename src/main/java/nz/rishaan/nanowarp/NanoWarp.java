package nz.rishaan.nanowarp;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Supplier;

public final class NanoWarp extends JavaPlugin {

	HashMap<String, Location> warpPoints;

	@Override
	public void onEnable() {
		// Plugin startup logic
		try {
			loadData();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic
		try {
			dataConfig.save(dataFile);
		} catch (IOException e) {
			System.out.println("An error occurred trying to save the warp points");
			e.printStackTrace();
		}
	}

	YamlConfiguration dataConfig;
	File dataFile;

	public void assertIsDouble(String path) throws Exception {
		if (!dataConfig.isDouble(path)) throw new Exception(String.format("'%s' does not contain a valid double", path));
	}

	public void loadData() throws Exception {
		getDataFolder().mkdirs();

		dataFile = new File(getDataFolder(), "data.yml");
		try {
			dataFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		dataConfig = YamlConfiguration.loadConfiguration(dataFile);

		warpPoints = new HashMap<>();

		for (String name : dataConfig.getKeys(false)) {
			String path_x = name + ".x";
			String path_y = name + ".y";
			String path_z = name + ".z";
			String path_world = name + ".world";

			assertIsDouble(path_x);
			assertIsDouble(path_y);
			assertIsDouble(path_z);

			double x = dataConfig.getDouble(path_x);
			double y = dataConfig.getDouble(path_y);
			double z = dataConfig.getDouble(path_z);
			String w = dataConfig.getString(path_world);

			if (w == null) {
				throw new Exception(String.format("'%s' does not exist", path_world));
			}

			World world;

			try {
				world = Bukkit.getServer().getWorld(UUID.fromString(w));
			} catch (IllegalArgumentException e) {
				throw new Exception(String.format("Invalid UUID %s", w));
			}

			Location location = new Location(world, x, y, z);
			warpPoints.put(name, location);
		}
	}


	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		switch (label.toLowerCase()) {
			case "warpcreate":
				return createCommand(sender, args);
			case "warpdelete":
				return deleteCommand(sender, args);
			case "warplist":
				return listCommand(sender, args);
			case "warp":
				return warpCommand(sender, args);
			default:
				return false;
		}
	}

	class InvalidWorldException extends Exception {}

	private Location locationFromStrings(String x, String y, String z, World world) throws NumberFormatException {

		return new Location(world, Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z));
	}
	private Location locationFromStrings(String x, String y, String z, String world) throws InvalidWorldException, NumberFormatException {
		World w = Bukkit.getWorld(world);
		if (w == null) throw new InvalidWorldException();

		return locationFromStrings(x, y, z, w);
	}

	private boolean createCommand(CommandSender sender, String[] args) {
		boolean isPlayer = sender instanceof Player;
		if (isPlayer && !sender.hasPermission("")) {
			sender.sendMessage("");
			return false;
		}

		Location location;
		String warpName;

		switch (args.length) {
			case 1:
				if (!isPlayer) {
					sender.sendMessage("Need to be a player to use this command with 1 argument");
					return false;
				}
				location = ((Player)sender).getLocation();
				// Strip rotation info
				location = new Location(location.getWorld(), location.getX(), location.getY(), location.getZ());
				warpName = args[0];
				break;
			case 4:
				if (!isPlayer) {
					sender.sendMessage("Need to be a player to use this command with 4 arguments");
					return false;
				}
				try {
					location = locationFromStrings(args[0], args[1], args[2], ((Player) sender).getWorld());
				} catch (NumberFormatException e) {
					sender.sendMessage(String.format("Invalid coordinates, x: %s, y: %s, z: %s", args[0], args[1], args[2]));
					return false;
				}
				warpName = args[3];
				break;
			case 5:
				try {
					location = locationFromStrings(args[0], args[1], args[2], args[3]);
				} catch (InvalidWorldException e) {
					sender.sendMessage(String.format("Invalid world, %s", args[3]));
					return false;
				} catch (NumberFormatException e) {
					sender.sendMessage(String.format("Invalid coordinates, x: %s, y: %s, z: %s", args[0], args[1], args[2]));
					return false;
				}
				warpName = args[4];
				break;
			default:
				sender.sendMessage("Invalid number of arguments");
				return false;
		}

		if (StringUtils.containsAny(warpName, " .")) {
			sender.sendMessage("Invalid warp name, ' ' & '.' are not allowed.");
			return false;
		}

		warpPoints.put(warpName, location);
		dataConfig.set(warpName + ".x", location.getX());
		dataConfig.set(warpName + ".y", location.getY());
		dataConfig.set(warpName + ".z", location.getZ());
		dataConfig.set(warpName + ".world", location.getWorld().getUID().toString());
		try {
			dataConfig.save(dataFile);
		} catch (IOException e) {
			sender.sendMessage("An internal error occurred");
			System.out.println("An error occurred trying to save the warp points");
			e.printStackTrace();
			return true;
		}
		sender.sendMessage(String.format("Created warp point, %s", warpName));
		return true;
	}

	private boolean deleteCommand(CommandSender sender, String[] args) {
		if (sender instanceof Player && !sender.hasPermission("")) {
			sender.sendMessage("No perm");
			return false;
		}

		if (args.length != 1) {
			sender.sendMessage("Invalid number of arguments");
			return false;
		}

		String warpName = args[0].toLowerCase();

		Location location = warpPoints.remove(warpName);

		if (location == null) {
			sender.sendMessage(String.format("Couldn't find warp point, %s", warpName));
		} else {
			sender.sendMessage(String.format("Deleted warp point, %s", warpName));
		}
		return true;
	}

	private boolean warpCommand(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("You need to be a player to use this command.");
			return true;
		}

		if (!sender.hasPermission("")) {
			sender.sendMessage("You don't have permission to use this command.");
			return true;
		}

		if (args.length == 0) {
			sender.sendMessage("Missing warp point name.");
			return false;
		}

		String warpName = args[0].toLowerCase();

		Location location = warpPoints.get(warpName);

		if (location == null) {
			sender.sendMessage(String.format("Couldn't find warp point, %s", warpName));
		} else {
			Player player = (Player) sender;

			boolean inRange = false;
			double playerX = player.getLocation().getX();
			double playerZ = player.getLocation().getZ();
			for (Location location1 : warpPoints.values()) {
				if (Math.abs(playerX - location1.getX()) < 10 && Math.abs(playerZ - location1.getZ()) < 10) {
					inRange = true;
					break;
				}
			}

			if (!inRange) {
				sender.sendMessage("You aren't in range of a warp point.");
				return true;
			}

			if (player.isInsideVehicle()) {
				Entity entity = player;
				while (entity.isInsideVehicle()) {
					entity = entity.getVehicle();
				}

				try {
					methods[1].invoke(methods[0].invoke(entity), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
				} catch (Exception ex) {
					ex.printStackTrace();
					player.sendMessage("An internal error occurred");
					return true;
				}
			} else {
				if (!player.teleport(location)) {
					player.sendMessage("Could not teleport for some unknown reason.");
					return true;
				}
			}
			player.sendMessage(String.format("Teleported to %s", warpName));
		}
		return true;
	}

	private boolean listCommand(CommandSender sender, String[] args) {
		if (args.length != 0) {
			sender.sendMessage("This command doesn't require any arguments");
			return false;
		}

		if (sender instanceof Player && !sender.hasPermission("")) {
			sender.sendMessage("You don't have permission to use this command.");
			return true;
		}

		sender.sendMessage(String.format("Warp points:\n%s", String.join(" ",warpPoints.keySet())));


		return true;
	}

	private final Method[] methods = ((Supplier<Method[]>) () -> {
		try {
			Method getHandle = Class.forName(Bukkit.getServer().getClass().getPackage().getName() + ".entity.CraftEntity").getDeclaredMethod("getHandle");
			return new Method[] {
					getHandle,
					getHandle.getReturnType().getDeclaredMethod("setPositionRotation", double.class, double.class, double.class, float.class, float.class)
			};
		} catch (Exception ex) {
			return null;
		}
	}).get();
}
