package com.github.unchama.seichistat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.github.unchama.seichistat.data.PlayerData;
import com.github.unchama.seichistat.util.Util;

//MySQL操作関数
public class Sql{
	private SeichiStat plugin = SeichiStat.plugin;
	private HashMap<UUID,PlayerData> playermap = SeichiStat.playermap;
	private final String url, db, id, pw;
	private Connection con = null;
	private Statement stmt = null;
	private ResultSet rs = null;
	public static String exc;

	//コンストラクタ
	Sql(SeichiStat plugin ,String url, String db, String id, String pw){
		this.plugin = plugin;
		this.url = url;
		this.db = db;
		this.id = id;
		this.pw = pw;
	}

	/**
	 * 接続関数
	 *
	 * @param url 接続先url
	 * @param id ユーザーID
	 * @param pw ユーザーPW
	 * @param db データベースネーム
	 * @param table テーブルネーム
	 * @return
	 */
	public boolean connect(){
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		} catch (InstantiationException | IllegalAccessException
				| ClassNotFoundException e) {
			e.printStackTrace();
			plugin.getLogger().info("Mysqlドライバーのインスタンス生成に失敗しました");
			return false;
		}
		//sql鯖への接続とdb作成
		if(!connectMySQL()){
			plugin.getLogger().info("SQL接続に失敗しました");
			return false;
		}
		if(!createDB()){
			plugin.getLogger().info("データベース作成に失敗しました");
			return false;
		}
		if(!connectDB()){
			plugin.getLogger().info("データベース接続に失敗しました");
			return false;
		}
		if(!createPlayerDataTable(SeichiStat.PLAYERDATA_TABLENAME)){
			plugin.getLogger().info("playerdataテーブル作成に失敗しました");
			return false;
		}

		return true;
	}

	private boolean connectMySQL(){
		try {
			if(stmt != null && !stmt.isClosed()){
				stmt.close();
				con.close();
			}
			con = (Connection) DriverManager.getConnection(url, id, pw);
			stmt = con.createStatement();
	    } catch (SQLException e) {
	    	e.printStackTrace();
	    	return false;
		}
		return true;
	}

	/**
	 * コネクション切断処理
	 *
	 * @return 成否
	 */
	public boolean disconnect(){
	    if (con != null){
	    	try{
	    		stmt.close();
				con.close();
	    	}catch (SQLException e){
	    		e.printStackTrace();
	    		return false;
	    	}
	    }
	    return true;
	}

	//コマンド出力関数
	//@param command コマンド内容
	//@return 成否
	//@throws SQLException
	private boolean putCommand(String command){
		try {
			stmt.executeUpdate(command);
			return true;
		} catch (SQLException e) {
			//接続エラーの場合は、再度接続後、コマンド実行
			java.lang.System.out.println("sqlクエリの実行に失敗しました。以下にエラーを表示します");
			exc = e.getMessage();
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * データベース作成
	 * 失敗時には変数excにエラーメッセージを格納
	 *
	 * @param table テーブル名
	 * @return 成否
	 */
	public boolean createDB(){
		if(db==null){
			return false;
		}
		String command;
		command = "CREATE DATABASE IF NOT EXISTS " + db
				+ " character set utf8 collate utf8_general_ci";
		return putCommand(command);
	}

	private boolean connectDB() {
		String command;
		command = "use " + db;
		return putCommand(command);
	}

	/**
	 * テーブル作成
	 * 失敗時には変数excにエラーメッセージを格納
	 *
	 * @param table テーブル名
	 * @return 成否
	 */
	public boolean createPlayerDataTable(String table){
		if(table==null){
			return false;
		}
		//テーブルが存在しないときテーブルを新規作成
		String command =
				"CREATE TABLE IF NOT EXISTS " + table +
				"(name varchar(30) unique," +
				"uuid varchar(128) unique)";
		if(!putCommand(command)){
			return false;
		}
		//必要なcolumnを随時追加
		command =
				"alter table " + table +
				" add column if not exists num_rgbreak int default 0" +
				",add column if not exists lastquit datetime default null" +
				",add column if not exists playtick int default 0" +
				",add column if not exists loginflag boolean default false" +
				",add column if not exists num_magmadabaa int default 0" +
				",add column if not exists num_chat int default 0" +
				",add column if not exists num_cheatdabaa int default 0" +
				",add column if not exists num_command int default 0" +
				",add index if not exists name_index(name)" +
				"";
		return putCommand(command);
	}

	public boolean loadPlayerData(final Player p) {
		String name = Util.getName(p);
		final UUID uuid = p.getUniqueId();
		final String struuid = uuid.toString().toLowerCase();
		String command = "";
		final String table = SeichiStat.PLAYERDATA_TABLENAME;
 		int count = -1;
 		//uuidがsqlデータ内に存在するか検索
 		//command:
 		//select count(*) from playerdata where uuid = 'struuid'
 		command = "select count(*) as count from " + table
 				+ " where uuid = '" + struuid + "'";
 		try{
			rs = stmt.executeQuery(command);
			while (rs.next()) {
				   count = rs.getInt("count");
				  }
			rs.close();
		} catch (SQLException e) {
			exc = e.getMessage();
			return false;
		}

 		if(count == 0){
 			//uuidが存在しない時の処理

 			//新しくuuidとnameを設定し行を作成
 			//insert into playerdata (name,uuid) VALUES('unchima','UNCHAMA')
 			command = "insert into " + table
 	 				+ " (name,uuid,loginflag) values('" + name
 	 				+ "','" + struuid+ "','1')";
 			if(!putCommand(command)){
 				return false;
 			}
 			//PlayerDataを新規作成
 			playermap.put(uuid, new PlayerData(p));
 			return true;

 		}else if(count == 1){
 			//uuidが存在するときの処理
 			if(SeichiStat.DEBUG){
 				p.sendMessage("sqlにデータが保存されています。");
 			}

 	 		Thread th = new Thread(new Runnable(){

 				@Override
 				public void run() {
 					//同ステートメントだとmysqlの処理がバッティングした時に止まってしまうので別ステートメントを作成する
 					Statement stmt2 = null;
 					try {
 						stmt2 = con.createStatement();
 					} catch (SQLException e1) {
 						e1.printStackTrace();
 					}
 					//同時にresultsetも別で作成しておく
 					ResultSet rs2 = null;

 					//loginflag判別処理
 					Boolean flag = true;
 					int i = 0;
 					String command = "";

 					//flagがfalseになるまで繰り返す
 					while(flag){
 			 	 		command = "select loginflag from " + table
 			 	 				+ " where uuid = '" + struuid + "'";
 			 	 		try{
 			 				rs2 = stmt2.executeQuery(command);
 			 				while (rs2.next()) {
 			 					   flag = rs2.getBoolean("loginflag");
 			 					  }
 			 				rs2.close();
 			 			} catch (SQLException e) {
 			 				java.lang.System.out.println("sqlクエリの実行に失敗しました。以下にエラーを表示します");
 			 				exc = e.getMessage();
 			 				e.printStackTrace();
 			 				return;
 			 			}
 			 	 		if(i >= 10&&flag){
 			 	 			//強制取得実行
 			 	 			plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + p.getName() + "のplayerdata強制取得実行(seichistat)");
 			 	 			break;
 			 	 		}
 			 	 		if(flag){
 			 	 			plugin.getServer().getConsoleSender().sendMessage(ChatColor.YELLOW + p.getName() + "のloginflag=false待機…(" + (i+1) + "回目)(seichistat)");
 			 	 			//次のリクエストまで待つ
 			 	 			try {
 								Thread.sleep(500);	//ここに待機時間を入れる(ms)
 							} catch (InterruptedException e) {
 								e.printStackTrace();
 							}
 			 	 		}
 			 	 		i++;
 					}

 		 			//loginflag書き換え処理
 		 			command = "update " + table
 								+ " set loginflag = true"
 								+ " where uuid like '" + struuid + "'";
 		 			try {
 		 				stmt2.executeUpdate(command);
 		 			} catch (SQLException e) {
 		 				java.lang.System.out.println("sqlクエリの実行に失敗しました。以下にエラーを表示します");
 		 				exc = e.getMessage();
 		 				e.printStackTrace();
 		 				return;
 		 			}

 		 			//PlayerDataを新規作成
 		 			PlayerData playerdata = new PlayerData(p);

 		 			//playerdataをsqlデータから得られた値で更新
 		 			command = "select * from " + table
 		 					+ " where uuid like '" + struuid + "'";
 		 			try{
 		 				rs2 = stmt2.executeQuery(command);
 		 				while (rs2.next()) {
 		 					//各種数値
 		 	 				playerdata.num_rgbreak = rs2.getInt("num_rgbreak");
 		 	 				playerdata.playtick = rs2.getInt("playtick");
 		 	 				playerdata.num_magmadabaa = rs2.getInt("num_magmadabaa");
 		 	 				playerdata.num_chat = rs2.getInt("num_chat");
 		 	 				playerdata.num_cheatdabaa = rs2.getInt("num_cheatdabaa");
 		 	 				playerdata.num_command = rs2.getInt("num_command");
 		 				  }
 		 				rs2.close();
 		 			} catch (SQLException e) {
 		 				java.lang.System.out.println("sqlクエリの実行に失敗しました。以下にエラーを表示します");
 		 				exc = e.getMessage();
 		 				e.printStackTrace();
 		 				return;
 		 			}
 		 			//念のためstatement閉じておく
 		 			try {
 						stmt2.close();
 					} catch (SQLException e) {
 						e.printStackTrace();
 					}

 		 			if(SeichiStat.DEBUG){
 		 				p.sendMessage("sqlデータで更新しました");
 		 			}
 		 			//更新したplayerdataをplayermapに追加
 		 			playermap.put(uuid, playerdata);
 		 			plugin.getServer().getConsoleSender().sendMessage(ChatColor.GREEN + p.getName() + "のプレイヤーデータ取得完了(seichistat)");
 		 			return;
 				}
 			});
 	 		th.start();
 	 		return true;







 			//loginflag判別処理
 			/*
 			Boolean flag = true;
 			int i = 0;
 			//flagがfalseになるまで繰り返す
 			while(flag){
	 	 		command = "select loginflag from " + table
	 	 				+ " where uuid = '" + struuid + "'";
	 	 		try{
	 				rs = stmt.executeQuery(command);
	 				while (rs.next()) {
	 					   flag = rs.getBoolean("loginflag");
	 					  }
	 				rs.close();
	 			} catch (SQLException e) {
	 				java.lang.System.out.println("sqlクエリの実行に失敗しました。以下にエラーを表示します");
	 				exc = e.getMessage();
	 				e.printStackTrace();
	 				return null;
	 			}
	 	 		if(i >= 2&&flag){
	 	 			//強制取得実行
	 	 			plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + p.getName() + "のplayerdata強制取得実行(SeichiStat)");
	 	 			break;
	 	 		}
	 	 		if(flag){
	 	 			plugin.getServer().getConsoleSender().sendMessage(ChatColor.YELLOW + p.getName() + "のloginflag=false待機…(" + (i+1) + "回目)(SeichiStat)");
	 	 		//次のリクエストまで待つ(ms)
	 	 			try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						// TODO 自動生成された catch ブロック
						e.printStackTrace();
					}
	 	 		}
	 	 		i++;
 			}
 			//loginflag書き換え処理
 			command = "update " + table
						+ " set loginflag = true"
						+ " where uuid like '" + struuid + "'";
 			if(!putCommand(command)){
 				return null;
 			}


 			//PlayerDataを新規作成
 			PlayerData playerdata = new PlayerData(p);

 			//sqlデータから得られた値で更新

 			command = "select * from " + table
 					+ " where uuid like '" + struuid + "'";
 			try{
 				rs = stmt.executeQuery(command);
 				while (rs.next()) {

 					//各種数値
 	 				playerdata.num_rgbreak = rs.getInt("num_rgbreak");
 	 				playerdata.playtick = rs.getInt("playtick");
 	 				playerdata.num_magmadabaa = rs.getInt("num_magmadabaa");
 	 				playerdata.num_chat = rs.getInt("num_chat");
 	 				playerdata.num_cheatdabaa = rs.getInt("num_cheatdabaa");
 	 				playerdata.num_command = rs.getInt("num_command");

 				  }
 				rs.close();
 			} catch (SQLException e) {
 				java.lang.System.out.println("sqlクエリの実行に失敗しました。以下にエラーを表示します");
 				exc = e.getMessage();
 				e.printStackTrace();
 				return null;
 			}
 			if(SeichiStat.DEBUG){
 				p.sendMessage("sqlデータで更新しました。");
 			}
 			//更新したplayerdataを返す
 			plugin.getServer().getConsoleSender().sendMessage(ChatColor.GREEN + p.getName() + "のSeichiStat読込完了");
 			//更新したplayerdataを返す
 			return playerdata;
 			*/
 		}else{
 			//mysqlに該当するplayerdataが2個以上ある時エラーを吐く
 			Bukkit.getLogger().info(Util.getName(p) + "のplayerdata読込時に原因不明のエラー発生(seichistat)");
 			return false;
 		}
	}
	public boolean savePlayerData(PlayerData playerdata) {
		//引数のplayerdataをsqlにデータを送信

		String table = SeichiStat.PLAYERDATA_TABLENAME;
		String struuid = playerdata.uuid.toString();
		String command = "";

		command = "update " + table
				+ " set"

				//名前更新処理
				+ " name = '" + playerdata.name + "'"

				//各種数値更新処理
				+ ",lastquit = cast( now() as datetime )"
				+ ",num_rgbreak = " + Integer.toString(playerdata.num_rgbreak)
				+ ",playtick = " + Integer.toString(playerdata.playtick)
				+ ",num_magmadabaa = " + Integer.toString(playerdata.num_magmadabaa)
				+ ",num_chat = " + Integer.toString(playerdata.num_chat)
				+ ",num_cheatdabaa = " + Integer.toString(playerdata.num_cheatdabaa)
				+ ",num_command = " + Integer.toString(playerdata.num_command)

				+ " where uuid like '" + struuid + "'";

		return putCommand(command);
	}

	//loginflagのフラグ折る処理(ondisable時とquit時に実行させる)
	public boolean logoutPlayerData(PlayerData playerdata) {
		String table = SeichiStat.PLAYERDATA_TABLENAME;
		String struuid = playerdata.uuid.toString();
		String command = "";

		command = "update " + table
				+ " set"

				//ログインフラグ折る
				+ " loginflag = false"

				+ " where uuid like '" + struuid + "'";

		return putCommand(command);

	}

}