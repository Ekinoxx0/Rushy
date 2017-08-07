package fr.rushy.rushy3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import fr.rushy.database.Database;

class RushPlayer
{
   Player player;
   boolean hasVoted = false;
   RushTeam team;
   public RushPlayer(Player player, RushTeam team)
   {
      this.player = player;
      this.team = team;
   }
}

class RushTeam
{
   String name;
   ChatColor color;
   int[] waitCoordinates = new int[3];
   int[] rushCoordinates = new int[3];
}

class RushType
{
   String name;
   int[] waitGhostCoordinates = new int[3];
   int[] rushGhostCoordinates = new int[3];
   int initialWaitTime;
   int initialRushTime;
   RushTeam[] teams;
}

class RushTeamHolder
{
   RushTeam team;
   ArrayList<RushPlayer> rushPlayers = new ArrayList<RushPlayer>();
   Team sbTeam;
}

class Rush
{
   private RushType type;
   private boolean started = false;
   private World world;
   private Sign sign;
   private final File waitDir;
   private final File rushDir;
   private String name;
   private int teamSize;
   private RushTeamHolder[] teamHolders;
   private Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
   private int taskId;
   private boolean starting = false;

   public Rush(String name, RushType type, Sign sign, int teamSize) throws IOException
   {
      this.name = name;
      this.type = type;

      teamHolders = new RushTeamHolder[type.teams.length];
      for(int i = 0; i < type.teams.length; ++i)
      {
         teamHolders[i] = new RushTeamHolder();
         teamHolders[i].team = type.teams[i];
         teamHolders[i].sbTeam = board.registerNewTeam(type.teams[i].name);
         teamHolders[i].sbTeam.setPrefix(type.teams[i].color + "");
         teamHolders[i].sbTeam.setAllowFriendlyFire(false);
      }

      this.sign = sign;
      this.teamSize = teamSize;
      waitDir = new File(Main.tempDir.getPath() + File.separator + name + Main.WAIT_MAP_SUFFIX);
      rushDir = new File(Main.tempDir.getPath() + File.separator + name + Main.RUSH_MAP_SUFFIX);


      Main.copy(new File(Main.mapsDir.getPath() + File.separator + type.name.toLowerCase() + Main.WAIT_MAP_SUFFIX), waitDir);
      deleteCrap(waitDir);
      Bukkit.getServer().createWorld(new WorldCreator(waitDir.getPath()));
      world = Bukkit.getServer().getWorld(waitDir.getPath());
      world.setAutoSave(false);
      world.setTime(type.initialWaitTime);
   }

   public World getWorld()
   {
      return world;
   }

   private void deleteCrap(File worldDir)
   {
      new File(worldDir.getPath() + File.separator + "session.lock").delete();
   }

   public RushTeamHolder getTeamHolder(RushTeam team)
   {
      for(RushTeamHolder rushTeamHolder : teamHolders)
      {
         if(rushTeamHolder.team == team)
            return rushTeamHolder;
      }

      return null;
   }

   public int getTeamSize(RushTeam team)
   {
      return getTeamHolder(team).rushPlayers.size();
   }

   public void tellTeam(RushTeam team, String message)
   {
      ArrayList<RushPlayer> rushPlayers = getTeamHolder(team).rushPlayers;
      for(RushPlayer rushPlayer : rushPlayers)
      {
         rushPlayer.player.sendMessage(message);
      }
   }

   public boolean isStarted()
   {
      return started;   
   }

   public boolean isStarting()
   {
      return starting;
   }

   public int getPlayersSize()
   {
      int size = 0;
      for(RushTeamHolder teamHolder : teamHolders)
      {
         size += teamHolder.rushPlayers.size();
      }
      return size;
   }

   public String getName()
   {
      return name;
   }

   public Sign getSign()
   {
      return sign;
   }

   public int getMaxPlayers()
   {
      return type.teams.length * teamSize;
   }

   public RushPlayer getRushPlayer(Player player)
   {
      for(RushTeamHolder teamHolder : teamHolders)
      {
         for(RushPlayer rushPlayer : teamHolder.rushPlayers)
         {
            if(rushPlayer.player == player)
               return rushPlayer;
         }
      }
      return null;
   }

   public RushTeam getTeam(String teamName)
   {
      for(RushTeam team : type.teams)
      {
         if(team.name.equalsIgnoreCase(teamName))
            return team;
      }
      return null;
   }

   public boolean removePlayer(Player player)
   {
      if(starting)
      {
         Main.msgWorld(world, ChatColor.RED + "Rush annulé, un joueur est parti");
         Bukkit.getScheduler().cancelTask(taskId);
         starting = false;
      }
      for(RushTeamHolder teamHolder : teamHolders)
      {
         for(RushPlayer rushPlayer : teamHolder.rushPlayers)
         {
            if(rushPlayer.player == player)
            {
               teamHolder.sbTeam.removePlayer(rushPlayer.player);
               player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
               teamHolder.rushPlayers.remove(rushPlayer);
               sign.setLine(Main.PLAYERS_LINE, this.getPlayersSize() + "/" + this.getMaxPlayers());
               sign.update(true);
               return true;
            }
         }
      }
      return false;
   }

   public int getTeamSize()
   {
      return teamSize;
   }

   private void spectate(Player player)
   {
      Main.goNaked(player);
      player.setGameMode(GameMode.SPECTATOR);
      player.setScoreboard(board);
   }

   public void spectateWait(Player player)
   {
      spectate(player);
      player.teleport(new Location(world, type.waitGhostCoordinates[0], type.waitGhostCoordinates[1], type.waitGhostCoordinates[2]));
   }

   public void spectateRush(Player player)
   {
      spectate(player);
      player.teleport(new Location(world, type.rushGhostCoordinates[0], type.rushGhostCoordinates[1], type.rushGhostCoordinates[2]));
   }

   public void joinTeam(RushTeam team, Player player)
   {
      RushTeamHolder teamHolder = this.getTeamHolder(team);
      removePlayer(player);
      teamHolder.rushPlayers.add(new RushPlayer(player, team));
      teamHolder.sbTeam.addPlayer(player);
      player.setScoreboard(board);
      Main.goNaked(player);
      player.teleport(new Location(world, team.waitCoordinates[0], team.waitCoordinates[1], team.waitCoordinates[2]));
      Main.msgWorld(world, ChatColor.GREEN + player.getName() + " a rejoint l'équipe " + team.name);
      sign.setLine(Main.PLAYERS_LINE, this.getPlayersSize() + "/" + this.getMaxPlayers());
      sign.update(true);
      if(getPlayersSize() == getMaxPlayers())
         start();
   }

   public void teamHasWon(RushTeamHolder teamHolder)
   {
      if(teamHolder == null)
         Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "Le rush " + name + " est fini personne n'a gagnée !");
      else
      {
         String winnerNames = "(";
         for(int i = 0; i < teamHolder.rushPlayers.size(); ++i)
         {
            RushPlayer rushPlayer = teamHolder.rushPlayers.get(i);
            Player p = rushPlayer.player;
            Database.updateCoinsAndMsg(p, Main.WIN_COINS);
            try
            {
               Database.addWin(p);
            }
            catch (SQLException e)
            {
               e.printStackTrace();
            }
            if(i != teamHolder.rushPlayers.size() - 1)
               winnerNames += p.getName() + ", ";
            else
               winnerNames += p.getName() + ")";
         }
         Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "Le rush " + name + " est fini l'equipe "
               + teamHolder.team.name + " " + winnerNames + " a gagnée !");
      }
   }

   public void resetRush()
   {
      for(Player player : world.getPlayers())
      {
         player.teleport(Main.getMainSpawn());
         player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
         Main.goNaked(player);
      }

      sign.setLine(Main.PLAYERS_LINE, "0/" + this.getMaxPlayers());
      Main.setStatus(sign, false);
      sign.getChunk().load(true);
      sign.update(true);
      Bukkit.unloadWorld(rushDir.getPath(), false);
      Main.deleteDirectory(rushDir);
      Main.games.remove(this);
   }

   public void start()
   {
      starting = true;
      taskId = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Main.plugin, new Runnable()
      {
         int i = 3;
         public void run()
         {
            Main.msgWorld(world, ChatColor.GREEN + "" + i);
            for(Player player : world.getPlayers())
               player.getWorld().playSound(player.getLocation(), Sound.NOTE_PLING, 1, 0);
            --i;
            if(i < 1)
            {
               try
               {
                  Main.copy(new File(Main.mapsDir.getPath() + File.separator + type.name.toLowerCase() + Main.RUSH_MAP_SUFFIX), rushDir);
                  deleteCrap(rushDir);
                  Bukkit.getServer().createWorld(new WorldCreator(rushDir.getPath()));
                  final List<Player> worldPlayers = world.getPlayers();
                  world = Bukkit.getServer().getWorld(rushDir.getPath());
                  world.setTime(type.initialRushTime);
                  world.setAutoSave(false);
                  started = true;
                  starting = false;
                  sign.getChunk().load(true);
                  Main.setStatus(sign, true);
                  sign.update(true);


                  for(RushTeamHolder teamHolder : teamHolders)
                  {
                     for(RushPlayer rushPlayer : teamHolder.rushPlayers)
                     {
                        Player player = rushPlayer.player;
                        Main.goNaked(player);
                        worldPlayers.remove(player);
                        player.teleport(new Location(world, teamHolder.team.rushCoordinates[0], teamHolder.team.rushCoordinates[1], 
                              teamHolder.team.rushCoordinates[2]));
                     }
                  }

                  for(Player player : worldPlayers)
                  {
                     spectateRush(player);
                  }
                  removeWait();
                  Main.msgWorld(world, ChatColor.GREEN + "Le rush commence ! Bonne Chance !");
               }
               catch(IOException e)
               {
                  e.printStackTrace();
               }
               Bukkit.getScheduler().cancelTask(taskId);
            }
         }
      }, 0L, 20L);
   }

   public void removeWait()
   {
      Bukkit.unloadWorld(waitDir.getPath(), false);
      Main.deleteDirectory(waitDir);
   }


   public String getLoseMessage(Player player)
   {
      return ChatColor.RED + player.getName() + " a perdu !";
   }

   public void lose(final Player player)
   {
      try
      {
         Database.addLose(player);
      }
      catch (SQLException e)
      {
         e.printStackTrace();
      }
      removePlayer(player);
      Main.goNaked(player);
      Main.msgWorld(world, getLoseMessage(player));
      spectateRush(player);
      RushTeamHolder lastTeamHolder = null;
      for(RushTeamHolder teamHolder : teamHolders)
      {
         if(teamHolder.rushPlayers.size() > 0)
         {
            if(lastTeamHolder != null)
               return;
            lastTeamHolder = teamHolder;
         }
      }

      teamHasWon(lastTeamHolder);
      resetRush();
   }

   public void vote(RushPlayer player)
   {
      player.hasVoted = true;
      Main.msgWorld(world, player.player.getName() + " a voté");
      for(RushTeamHolder teamHolder : teamHolders)
      {
         if(teamHolder.rushPlayers.size() == 0)
            return;
         for(RushPlayer rushPlayer : teamHolder.rushPlayers)
         {
            if(!rushPlayer.hasVoted)
               return;
         }
      }
      start();
   }
}

public class Main extends JavaPlugin
{
   static File dataDir;
   static Plugin plugin;
   static File mapsDir;
   static File tempDir;
   static final String WAIT_MAP_SUFFIX = "_wait";
   static final String RUSH_MAP_SUFFIX = "_rush";
   static ArrayList<Rush> games = new ArrayList<Rush>();
   static RushType[] rushTypes = new RushType[2];
   static final ChatColor PREFIX_COLOUR = ChatColor.DARK_RED;
   static final int TITLE_LINE = 0;
   static final int STATUS_LINE = 1;
   static final int PLAYERS_LINE = 2;
   static final int NAME_LINE = 3;
   static final String NOT_IN_RUSH = ChatColor.RED + "Vous n'êtes pas en Rush";
   static final String GAME_STARTED = ChatColor.RED + "Le jeux est en cours";
   static final String RUSH_STARTING = ChatColor.RED + "Le rush commence...";
   static final int KILL_COINS = 5;
   static final int WIN_COINS = 20;

   @Override
   public void onEnable()
   { 
      Database.connect();
      Main.dataDir = getDataFolder();
      if(!dataDir.exists())
         dataDir.mkdir();
      mapsDir = new File(dataDir.getPath() + File.separator + "maps");
      if(!mapsDir.exists())
         mapsDir.mkdir();
      tempDir = new File(dataDir.getPath() + File.separator + "temp");
      if(!tempDir.exists())
         tempDir.mkdir();
      plugin = this;
      purgeDirectory(tempDir);
      getServer().getPluginManager().registerEvents(new EventListener(), this); 

      int initialMapTime = 16000;
      int[] waitGhostCoordinates = {1036, 37, -644};
      int[] rushGhostCoordinates = {-0, 66, -0};
      RushType classic = new RushType();
      classic.name = "Rush";
      classic.initialWaitTime = initialMapTime;
      classic.initialRushTime = initialMapTime;
      classic.waitGhostCoordinates = waitGhostCoordinates;
      classic.rushGhostCoordinates = rushGhostCoordinates;
      classic.teams = new RushTeam[2];
      //Information des Teams
      //Equipe Rouge
      RushTeam redTeam = new RushTeam();
      redTeam.name = "Red";
      redTeam.color = ChatColor.RED;
      redTeam.waitCoordinates = new int[]{1036, 35, -636};
      redTeam.rushCoordinates = new int[]{-2, 53, 62};

      classic.teams[0] = redTeam;
      //Equipe Bleu
      RushTeam blueTeam = new RushTeam();
      blueTeam.name = "Blue";
      blueTeam.color = ChatColor.DARK_AQUA;
      blueTeam.waitCoordinates = new int[]{1036, 35, -652};
      blueTeam.rushCoordinates = new int[]{-2, 53, -63};
      classic.teams[1] = blueTeam;

      rushTypes[0] = classic;

      RushType fourTeams = new RushType();
      fourTeams.name = "Rush4";
      fourTeams.initialWaitTime = initialMapTime;
      fourTeams.initialRushTime = initialMapTime;
      fourTeams.waitGhostCoordinates = waitGhostCoordinates;
      fourTeams.rushGhostCoordinates = rushGhostCoordinates;
      fourTeams.teams = new RushTeam[4];

      fourTeams.teams[0] = redTeam;
      fourTeams.teams[1] = blueTeam;
      //Equipe Verte
      fourTeams.teams[2] = new RushTeam();
      fourTeams.teams[2].name = "Green";
      fourTeams.teams[2].color = ChatColor.GREEN;
      fourTeams.teams[2].waitCoordinates = new int[]{1056, 35, -643};
      fourTeams.teams[2].rushCoordinates = new int[]{-64, 53, -1};
      //Equipe violet
      fourTeams.teams[3] = new RushTeam();
      fourTeams.teams[3].name = "Purple";
      fourTeams.teams[3].color = ChatColor.LIGHT_PURPLE;
      fourTeams.teams[3].waitCoordinates = new int[]{1016, 35, -647};
      fourTeams.teams[3].rushCoordinates = new int[]{59, 53, -1};
      rushTypes[1] = fourTeams;
   }


   @Override
   public void onDisable()
   {
      for(Player player : Bukkit.getOnlinePlayers())
      {
         player.teleport(getMainSpawn());
         goNaked(player);
         showHidePlayers(player, player.getWorld());
         player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
      }
      for(Rush rush : games)
      {
         Sign sign = rush.getSign();
         sign.getChunk().load(true);
         setStatus(sign, false);
         sign.setLine(PLAYERS_LINE, "0/" + rush.getMaxPlayers());
         sign.update();
         Bukkit.unloadWorld(rush.getWorld(), false);
      }
   }

   Rush getRush(String name)
   {
      for(Rush rush : games)
      {
         if(rush.getName().equals(name))
            return rush;
      }
      return null;
   }

   Rush getRush(World world)
   {
      for(Rush rush : games)
      {
         if(rush.getWorld() == world)
            return rush;
      }
      return null;
   }

   Rush getRush(Player player)
   {
      for(Rush rush : games)
      {
         if(rush.getRushPlayer(player) != null)
            return rush;
      }
      return null;
   }

   class EventListener implements Listener
   {
      @EventHandler
      public void onPlayerJoin(PlayerJoinEvent event)
      {
         Player player = event.getPlayer();
         goNaked(player);
         player.teleport(getMainSpawn());
         showHidePlayers(player, player.getWorld());
         event.setJoinMessage(null);
      }

      @EventHandler
      public void onPlaterQuit(PlayerQuitEvent event)
      {
         Player player = event.getPlayer();
         Rush rush = getRush(player);
         if(rush != null)
         {
            if(rush.isStarted())
            {
               rush.lose(player);
            }
            else
               rush.removePlayer(player);
         }
         event.setQuitMessage(null);
      }

      @EventHandler
      public void onPlayerKick(PlayerKickEvent event)
      {
         event.setLeaveMessage(null);
      }

      @EventHandler
      public void onPlayerFoodLevelChange(FoodLevelChangeEvent event)
      {
         if(event.getEntity().getWorld().getName().contains(Main.WAIT_MAP_SUFFIX))
            event.setCancelled(true);
      }

      @EventHandler
      public void onPlayerDeath(final PlayerDeathEvent event)
      {
         final Player player = event.getEntity();
         final Rush rush = getRush(player.getWorld());
         if (rush != null)
         {
            if(rush.getRushPlayer(player) != null)
            {
               if(rush.isStarted())
               {
                  try
                  {
                     Database.addDeath(player);
                  }
                  catch (SQLException e)
                  {
                     e.printStackTrace();
                  }
                  if(player.getKiller() instanceof Player)
                  {
                     Player killer = (Player) player.getKiller();
                     if(killer != null)
                     {
                        Database.updateCoinsAndMsg(killer, KILL_COINS);
                        try
                        {
                           Database.addKill(player);
                        }
                        catch (SQLException e)
                        {
                           e.printStackTrace();
                        }
                     }
                  }
                  msgWorld(rush.getWorld(), event.getDeathMessage());
                  if(player.getBedSpawnLocation() == null)
                  {
                     rush.lose(player);
                  }
                  else
                  {
                     Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
                     {
                        public void run()
                        {
                           player.spigot().respawn();
                        }
                     }, 20L * 5);
                  }
               }
               else
               {
                  rush.removePlayer(player);
                  rush.spectateWait(player);
               }
            }
            else
            {
               goNaked(player);
               player.teleport(getMainSpawn());
            }
         }
         event.setDeathMessage(null);
      }

      @EventHandler
      public void OnPlayerRespawn(PlayerRespawnEvent event)
      {
         Player player = event.getPlayer();
         Rush rush = getRush(player);
         if (rush != null)
         {
            if(rush.isStarted())
            {
               if(player.getBedSpawnLocation() == null)
               {
                  rush.lose(player);
                  player.sendMessage(rush.getLoseMessage(player));
                  player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                  for(Player p : Bukkit.getWorlds().get(0).getPlayers()) //to fix floating head glitch
                  {
                     player.hidePlayer(p);
                     player.showPlayer(p);
                  }
               }
            }
         }
      }

      @EventHandler
      public void onPlayerChat(AsyncPlayerChatEvent event)
      {
         Player player = event.getPlayer();
         String message = event.getMessage();
         msgWorld(player.getWorld(), "<" + player.getDisplayName() + "> " + message);
         event.setCancelled(true);
      }

      @EventHandler
      public void onPlayerDamage(EntityDamageEvent event)
      {
         if(event.getEntity() instanceof Player)
         {
            Player player = (Player) event.getEntity();
            if(player.getWorld().equals(getMainSpawn().getWorld()))
               event.setCancelled(true);
         }
      }

      @EventHandler
      public void onPlayerBedEnter(PlayerBedEnterEvent event)
      {
         final Player player = event.getPlayer();
         Rush rush = getRush(player);
         if (rush != null)
         {
            if (player.getBedSpawnLocation() != null && rush.isStarted())
            {
               player.sendMessage(ChatColor.RED + "Tu as déjà dormis !");
               event.setCancelled(true);
            }
         }
      }

      @EventHandler
      public void onBlockBreak(BlockBreakEvent event)
      {
         Player player = event.getPlayer();
         Block block = event.getBlock();
         if(!player.isOp())
         {
            Rush rush = getRush(player);
            if(rush != null)
            {
               if(rush.isStarted())
               {
                  if(block.getType() == Material.OBSIDIAN)
                     event.setCancelled(true);
               }
               else
               {
                  event.setCancelled(true);
               }
            }
            else
               event.setCancelled(true);
         }
      }

      @EventHandler
      public void onBlockPlace(BlockPlaceEvent event)
      {
         Player player = event.getPlayer();
         Block block = event.getBlock();
         if(!player.isOp())
         {
            Rush rush = getRush(player);
            if(rush != null)
            {
               if(rush.isStarted())
               {
                  if(block.getType() == Material.BRICK)
                     event.setCancelled(true);
               }
               else
               {
                  event.setCancelled(true);
               }
            }
            else
               event.setCancelled(true);
         }
      }

      @EventHandler
      public void onCreatureSpawn(CreatureSpawnEvent event)
      {
         if(event.getLocation().getWorld().getName().contains(Main.dataDir.getPath()))
         {
            event.setCancelled(true);
         }
      }

      @EventHandler
      public void onWorldChanged(PlayerChangedWorldEvent event)
      {
         Player player = event.getPlayer();
         if(event.getFrom().getName().contains(Main.WAIT_MAP_SUFFIX) && !player.getWorld().getName().contains(Main.RUSH_MAP_SUFFIX))
         {
            if(event.getFrom().getPlayers().size() == 0)
            {
               Rush rush = getRush(event.getFrom());
               if(!rush.isStarted())
               {
                  rush.removeWait();
                  Main.games.remove(rush);
               }
            }
         }
         showHidePlayers(player, player.getWorld());
      }

      @EventHandler
      public void onWeatherChange(WeatherChangeEvent event)
      {
         event.setCancelled(true);
      }

      @EventHandler
      public void onInteract(PlayerInteractEvent event)
      {
         Player player = event.getPlayer();
         if (event.getAction() == Action.RIGHT_CLICK_BLOCK)
         {
            Block block = event.getClickedBlock();
            if (block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST)
            {
               Sign sign = (Sign)block.getState();
               String title = sign.getLine(TITLE_LINE);
               if(title.isEmpty())
                  return;
               boolean create = true;
               if(title.startsWith(Main.PREFIX_COLOUR + ""))
               {               
                  title = title.replace(Main.PREFIX_COLOUR + "", "");
                  create = false;
               }

               title = title.substring(1, title.length() - 1);

               RushType type = null;
               for(RushType rushType : rushTypes)
               {
                  if(rushType.name.equalsIgnoreCase(title))
                     type = rushType;
               }

               if(type != null)
               {
                  String name = sign.getLine(NAME_LINE);
                  int maxPlayers = Integer.parseInt(sign.getLine(Main.PLAYERS_LINE).split("/")[1]);
                  if(create)
                  {
                     sign.setLine(Main.TITLE_LINE, Main.PREFIX_COLOUR + "[" + title + "]");
                     setStatus(sign, false);
                     sign.setLine(Main.PLAYERS_LINE, "0/" + maxPlayers);
                     sign.setLine(Main.NAME_LINE, name);
                     sign.update(true);
                  }
                  else
                  {
                     Rush rush = getRush(name);
                     if(rush == null)
                     {
                        try
                        {
                           rush = new Rush(name, type, sign, maxPlayers / type.teams.length);
                           games.add(rush);
                        }
                        catch (IOException e)
                        {
                           e.printStackTrace();
                        }
                     }
                     if(!rush.isStarted())
                        rush.spectateWait(player);
                     else
                        rush.spectateRush(player);
                  }
               }
            }
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
   {
      if (cmd.getName().equalsIgnoreCase("join"))
      {
         if (sender instanceof Player)
         {
            Player player = (Player) sender;
            if(args.length == 1)
            {
               Rush rush = getRush(player.getWorld());
               if(rush != null)
               {
                  if(!rush.isStarted())
                  {
                     RushTeam team = null;
                     team = rush.getTeam(args[0]);
                     if(team != null)
                     {
                        if(!rush.isStarting())
                        {
                           RushPlayer rushPlayer = rush.getRushPlayer(player);
                           if(rushPlayer == null || rushPlayer.team != team)
                           {
                              if(rush.getTeamSize(team) != rush.getTeamSize())
                                 rush.joinTeam(team, player);
                              else
                                 player.sendMessage(ChatColor.RED + "Cette équipe est pleine");
                           }
                           else
                              player.sendMessage(ChatColor.RED + "Tu est déjà dans cette équipe");
                        }
                        else
                           player.sendMessage(RUSH_STARTING);
                     }
                     else
                        player.sendMessage(ChatColor.RED + "Cette équipe n'existe pas");

                  }
                  else
                     player.sendMessage(GAME_STARTED);
               }
               else
                  player.sendMessage(NOT_IN_RUSH);
            }
            else
               return false;
         }
      }
      else if(cmd.getName().equalsIgnoreCase("vote"))
      {
         if (sender instanceof Player)
         {
            Player player = (Player) sender;
            Rush rush = getRush(player);
            if(rush != null)
            {
               if(!rush.isStarted())
               {
                  RushPlayer rushPlayer = rush.getRushPlayer(player);
                  if(!rush.isStarting())
                  {
                     if(!rushPlayer.hasVoted)
                        rush.vote(rushPlayer);
                     else
                        player.sendMessage(ChatColor.RED + "Tu as déjà voté");
                  }
                  else
                     player.sendMessage(RUSH_STARTING);  
               }
               else
                  player.sendMessage(GAME_STARTED);
            }
            else
               player.sendMessage(NOT_IN_RUSH);
         }
      }
      else if(cmd.getName().equalsIgnoreCase("start"))
      {
         if (sender instanceof Player)
         {
            Player player = (Player) sender;
            Rush rush;
            if(args.length == 1)
            {
               rush = getRush(args[0]);
               if(rush != null)
               {
                  rush.start();
                  player.sendMessage(ChatColor.GREEN + "Forced");
               }
               else
                  player.sendMessage(ChatColor.RED + "Le rush n'existe pas");
            }
            else
            {
               rush = getRush(player);
               if(rush != null)
               {
                  if(!rush.isStarted())
                     rush.start();
                  else
                     player.sendMessage(GAME_STARTED);
               }
               else
                  player.sendMessage(NOT_IN_RUSH);
            }

         }
      }
      else if(cmd.getName().equalsIgnoreCase("t"))
      {
         if (sender instanceof Player)
         {
            Player player = (Player) sender;
            if(args.length > 0)
            {
               Rush rush = getRush(player);
               if(rush != null)
               {
                  String message = "";
                  for(String part : args)
                  {
                     message += part + " ";
                  }
                  RushTeam team = rush.getRushPlayer(player).team;
                  rush.tellTeam(team, "[" + player.getName() + "->" + team.color + "Team" + ChatColor.RESET + "] " + message);
               }
               else
                  player.sendMessage(NOT_IN_RUSH);
            }
            else
               return false;
         }
      }
      else if(cmd.getName().equalsIgnoreCase("leave"))
      {
         if (sender instanceof Player)
         {
            Player player = (Player) sender;
            Rush rush = getRush(player);
            if(rush != null)
            {
               if(rush.isStarted())
                  rush.lose(player);
               else
                  rush.removePlayer(player);
            }
            player.teleport(getMainSpawn());
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            goNaked(player);
         }
      }
      else if(cmd.getName().equalsIgnoreCase("spect"))
      {
         if (sender instanceof Player)
         {
            Player player = (Player) sender;
            Rush rush = getRush(player);
            if(rush != null)
            {
               if(rush.isStarted())
                  rush.lose(player);
               else
               {
                  rush.removePlayer(player);
                  rush.spectateWait(player);
               }
            }
            else
               player.sendMessage(NOT_IN_RUSH);
         }
      }
      return true;
   }

   static void setStatus(Sign sign, Boolean started)
   {
      sign.setLine(STATUS_LINE, started ? ChatColor.DARK_GRAY + "En cours" : ChatColor.DARK_BLUE + "En attente");
   }

   static void copy(File sourceLocation, File targetLocation) throws IOException
   {
      if(sourceLocation.isDirectory())
      {
         copyDirectory(sourceLocation, targetLocation);
      }
      else
      {
         copyFile(sourceLocation, targetLocation);
      }
   }

   static void copyDirectory(File source, File target) throws IOException
   {
      if(!target.exists())
      {
         target.mkdir();
      }

      for (String f : source.list())
      {
         copy(new File(source, f), new File(target, f));
      }
   }

   static void copyFile(File source, File target) throws IOException
   {
      InputStream in = new FileInputStream(source);
      OutputStream out = new FileOutputStream(target);
      try
      {
         byte[] buf = new byte[1024];
         int length;
         while ((length = in.read(buf)) > 0)
         {
            out.write(buf, 0, length);
         }
      }
      finally
      {
         in.close();
         out.close();
      }
   }

   static boolean deleteDirectory(File directory)
   {
      if(directory.exists())
      {
         File[] files = directory.listFiles();
         if(files != null)
         {
            for(int i = 0; i < files.length; i++)
            {
               if(files[i].isDirectory())
                  deleteDirectory(files[i]);
               else
                  files[i].delete();
            }
         }
      }
      return(directory.delete());
   }

   static void purgeDirectory(File dir)
   {
      for (File file: dir.listFiles())
      {
         if (file.isDirectory()) purgeDirectory(file);
         file.delete();
      }
   }

   static Location getMainSpawn()
   {
      return Bukkit.getWorlds().get(0).getSpawnLocation();
   }

   static void goNaked(Player player)
   {
      player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
      player.getInventory().clear();
      player.updateInventory();
      player.setHealth(20.0);
      player.setFoodLevel(20);
      player.setGameMode(GameMode.SURVIVAL);
      player.setBedSpawnLocation(null);
      player.setFallDistance(0);
      player.setExp(0);
      for(PotionEffect effect : player.getActivePotionEffects())
      {
         player.removePotionEffect(effect.getType());
      }
      player.setFireTicks(0);
   }

   static void showHidePlayers(Player player, World w)
   {
      for(Player p : Bukkit.getOnlinePlayers())
      {
         if(p.getWorld() == w)
         {
            p.showPlayer(player);
            player.showPlayer(p);
         }
         else
         {
            p.hidePlayer(player);
            player.hidePlayer(p);
         }
      }
   }

   static void msgWorld(World w, String msg)
   {
      for (Player player : w.getPlayers())
         player.sendMessage(msg);
   }
}
