package fr.rushy.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class Database
{
   static String url = "jdbc:mysql://mc.rushy.fr:3306/Rushy";
   static String user = "rushy";
   static String pass = "@SQL987";
   static Connection conn = null;

   public static void connect()
   {
      try
      {
         conn = DriverManager.getConnection(url, user, pass);
      }

      catch (SQLException e)
      {
         e.printStackTrace();
         Bukkit.getServer().shutdown();
      }
   }

   public static boolean playerExists(Player player) throws SQLException
   {    
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("SELECT uuid FROM users WHERE uuid = ?");
      try
      {
         queryStatement.setString(1, player.getUniqueId().toString());
         queryStatement.executeQuery();
         ResultSet resultSet = queryStatement.getResultSet();
         return resultSet.next();
      }
      finally
      {
         queryStatement.close();
      }
   }
   
   public static void updateName(Player player) throws SQLException
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("UPDATE users SET name = ? WHERE uuid = ?");
      try
      {
         queryStatement.setString(1, player.getName());
         queryStatement.setString(2, player.getUniqueId().toString());
         queryStatement.executeUpdate();
      }
      finally
      {
         queryStatement.close();
      }
      
   }
   
   public static String getPlayerUUID(String name) throws SQLException
   {    
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("SELECT uuid FROM users WHERE name = ?");
      try
      {
         queryStatement.setString(1, name);
         queryStatement.executeQuery();
         ResultSet resultSet = queryStatement.getResultSet();
         resultSet.next();
         return resultSet.getString(1);
      }
      finally
      {
         queryStatement.close();
      }
   }

   public static void insertMember(Player player) throws SQLException
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("INSERT INTO users(uuid, name, joined) VALUES (?, ?, NOW())");
      try
      {
         queryStatement.setString(1, player.getUniqueId().toString());
         queryStatement.setString(2, player.getName());
         queryStatement.executeUpdate();
      }
      finally
      {
         queryStatement.close();
      }
      
      queryStatement = (PreparedStatement) conn.prepareStatement("INSERT INTO stats(user_id) SELECT id FROM users WHERE uuid = ?");
      try
      {
         queryStatement.setString(1, player.getUniqueId().toString());
         queryStatement.executeUpdate();
      }
      finally
      {
         queryStatement.close();
      }
   }

   public static int getTrail(Player player) throws SQLException
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("SELECT trail FROM users WHERE uuid = ?");
      try
      {
         queryStatement.setString(1, player.getUniqueId().toString());
         queryStatement.executeQuery();
         ResultSet resultSet = queryStatement.getResultSet();
         resultSet.next();
         return resultSet.getInt(1);
      }
      finally
      {
         queryStatement.close();
      }
   }

   public static void setTrail(Player player, int trail) throws SQLException
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("UPDATE users SET trail = ? WHERE uuid = ?");
      try
      {
         queryStatement.setInt(1, trail);
         queryStatement.setString(2, player.getUniqueId().toString());
         queryStatement.executeUpdate();
      }
      finally
      {
         queryStatement.close();
      }
   }

   public static int getCoins(Player player) throws SQLException
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("SELECT coins FROM users WHERE uuid = ?");
      try
      {
         queryStatement.setString(1, player.getUniqueId().toString());
         queryStatement.executeQuery();
         ResultSet resultSet = queryStatement.getResultSet();
         resultSet.next();
         return resultSet.getInt(1);
      }
      finally
      {
         queryStatement.close();
      }
   }

   public static void updateCoinsAndMsg(Player player, int coins)
   {
      try
      {
         if(coins < 0)
            player.sendMessage(ChatColor.RED + "" + coins + " RC");
         else
            player.sendMessage(ChatColor.GREEN + "+" + coins + " RC");
         updateCoins(player, coins);
      }
      catch(SQLException e)
      {
         e.printStackTrace();
      }
   }

   public static void updateCoins(Player player, int coins) throws SQLException //you can remove coins by using a negative number
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("UPDATE users SET coins = coins + ? WHERE uuid = ?");
      try
      {
         queryStatement.setInt(1, coins);
         queryStatement.setString(2, player.getUniqueId().toString());
         queryStatement.executeUpdate();
      }
      finally
      {
         queryStatement.close();
      }
   }
   
   public static void addKill(Player player) throws SQLException
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("UPDATE stats SET kills = kills + 1 WHERE user_id = (SELECT id FROM users WHERE uuid = ?)");
      try
      {
         queryStatement.setString(1, player.getUniqueId().toString());
         queryStatement.executeUpdate();
      }
      finally
      {
         queryStatement.close();
      }
   }

   public static void addDeath(Player player) throws SQLException
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("UPDATE stats SET deaths = deaths + 1 WHERE user_id = (SELECT id FROM users WHERE uuid = ?)");
      try
      {
         queryStatement.setString(1, player.getUniqueId().toString());
         queryStatement.executeUpdate();
      }
      finally
      {
         queryStatement.close();
      }
   }
   
   public static void addLose(Player player) throws SQLException
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("UPDATE stats SET loses = loses + 1 WHERE user_id = (SELECT id FROM users WHERE uuid = ?)");
      try
      {
         queryStatement.setString(1, player.getUniqueId().toString());
         queryStatement.executeUpdate();
      }
      finally
      {
         queryStatement.close();
      }
   }
   
   public static void addWin(Player player) throws SQLException
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("UPDATE stats SET wins = wins + 1 WHERE user_id = (SELECT id FROM users WHERE uuid = ?)");
      try
      {
         queryStatement.setString(1, player.getUniqueId().toString());
         queryStatement.executeUpdate();
      }
      finally
      {
         queryStatement.close();
      }
   }
   
   public void resetStats(Player player) throws SQLException
   {
      PreparedStatement queryStatement = (PreparedStatement) conn.prepareStatement("UPDATE stats SET wins = 0, loses = 0, kills = 0, deaths = 0 WHERE user_id = (SELECT id FROM users WHERE uuid = ?)");
      try
      {
         queryStatement.setString(1, player.getUniqueId().toString());
         queryStatement.executeUpdate();
      }
      finally
      {
         queryStatement.close();
      }
   }
}