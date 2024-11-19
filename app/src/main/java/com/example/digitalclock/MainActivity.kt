package com.example.digitalclock
import androidx.core.content.res.ResourcesCompat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.os.Build


class MainActivity : AppCompatActivity() {
    //機種選択
    //OPPO = 1
    //Pixel = 2
//    private var model = 1
    // デバイス情報に基づいてmodelの値を設定
    private var model = when {
        Build.MANUFACTURER.equals("oppo", ignoreCase = true) && Build.MODEL.equals("CPH2199", ignoreCase = true) -> 1
        Build.MANUFACTURER.equals("google", ignoreCase = true) && Build.MODEL.equals("Pixel 7a", ignoreCase = true) -> 2
        else -> 1 // その他の機種
    }

    //時計の表示を手動で変更
    private var manualId = 1

    //時計
    private lateinit var hourTextView: TextView
    private lateinit var colon1View: TextView
    private lateinit var minuteTextView: TextView
    private lateinit var colon2View: TextView
    private lateinit var secondTextView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var clockConfig: JsonObject
    private lateinit var taskConfig: JsonObject

    // タスク設定変数
    private var startTime: Calendar = Calendar.getInstance()// 初期時間（仮）
    private var intervalTime = 1 // ミッション1（仮）
    private var taskDuration = 1 // タスク時間（仮）

    // startTime を解析して設定
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // クラスのプロパティとして taskCounts を定義
    private var taskCounts: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
         setContentView(R.layout.activity_main) // 横向きレイアウトのまま。これは削除しないといけない

        // 各 TextView を取得
        hourTextView = findViewById(R.id.hourTextView)
        colon1View = findViewById(R.id.colon1View)
        minuteTextView = findViewById(R.id.minuteTextView)
        colon2View = findViewById(R.id.colon2View)
        secondTextView = findViewById(R.id.secondTextView)

        // フォントの設定
        val typeface = ResourcesCompat.getFont(this, R.font.digital_7)
        hourTextView.typeface = typeface
        colon1View.typeface = typeface
        minuteTextView.typeface = typeface
        colon2View.typeface = typeface
        secondTextView.typeface = typeface



        // JSON設定を読み込む
        taskConfig = loadJsonConfig("task_config.json")



        if(model == 1) {
            clockConfig = loadJsonConfig("clock_config_OPPO.json")
            //タスクプロファイルを選択
            selectTaskProfile("task1") // "task1"を選択
            // 初回実行時にタスク設定を準備する
            prepareTaskConfigs()
            //時計を表示
            updateTime()
        }
        else if(model == 2) {
            clockConfig = loadJsonConfig("clock_config_Pixel.json")
            //タスクプロファイルを選択
            selectTaskProfile("task2") // "task2"を選択
            // 初回実行時にタスク設定を準備する
            prepareTaskConfigs()
            //時計を表示
            manualUpdateTime()
        }


        // システムUIを非表示に設定
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    // タスク設定を取得する関数
    private fun selectTaskProfile(taskId: String) {
        // タスク設定から指定した taskId のタスクを取得
        val tasks = taskConfig.getAsJsonArray("tasks")
        val selectedTask = tasks.firstOrNull { it.asJsonObject.get("id").asString == taskId }?.asJsonObject
            ?: throw IllegalArgumentException("Task with id $taskId not found.")

        // 選択されたタスクの設定を読み込む
        val startTimeString = selectedTask.get("startTime").asString
        intervalTime= selectedTask.get("intervalTime").asInt
        taskDuration = selectedTask.get("taskDuration").asInt

        // startTime を解析して設定
        val taskTime = timeFormat.parse(startTimeString) ?: Date()

        // startTime を現在の日付 + 指定の時刻に設定
        startTime = Calendar.getInstance().apply {
            time = taskTime
            val currentDay = Calendar.getInstance()                    // 現在の日付を取得
            set(Calendar.YEAR, currentDay.get(Calendar.YEAR))          // 年を現在の年に設定
            set(Calendar.MONTH, currentDay.get(Calendar.MONTH))        // 月を現在の月に設定
            set(Calendar.DAY_OF_MONTH, currentDay.get(Calendar.DAY_OF_MONTH))  // 日を現在の日に設定
        }
    }

    private fun loadJsonConfig(fileName: String): JsonObject {
        // JSON ファイルを assets フォルダから読み込む
        val inputStream = assets.open(fileName)
        val reader = InputStreamReader(inputStream)
        return Gson().fromJson(reader, JsonObject::class.java).also { reader.close() }
    }

    // タスクの時間範囲と設定IDを保持するデータクラス
    data class TaskTimeConfig1(
        val configId: Long, // 設定ID 0=デフォルト, 1=3分前～1分前, 2=1分前～1分後
        val taskTime: Long, // タスクの終了時刻
        val threeMinutesBefore: Long, // 3分前
        val oneMinuteBefore: Long, // 1分前
        val oneMinuteAfter: Long, // 1分後
    )

    data class TaskTimeConfig2(
        val configId: Long, // 設定ID 0=デフォルト, 1=3分前～1分前, 2=1分前～1分後
        val taskTime: Long, // タスクの終了時刻
        val threeMinutesBefore: Long, // 3分前
        val oneMinuteBefore: Long, // 1分前
        val oneMinuteAfter: Long, // 1分後
    )

    // 各タスクの時間範囲をリストに保存
    val taskConfigs = mutableListOf<TaskTimeConfig1>()


    // タスク範囲を事前に計算してリストに格納する関数（ミッション1）
    private fun prepareTaskConfigs() {

        if (taskDuration % intervalTime == 0) {
            taskCounts = (taskDuration / intervalTime - 1).toLong()  // Int を Long に変換
        } else {
            taskCounts = (taskDuration / intervalTime).toLong()  // Int を Long に変換
        }


        for (taskNumber in 1..taskCounts) {
            val taskDurationInMillis = (taskNumber * intervalTime * 60 * 1000).toLong()
            val taskTime = startTime.timeInMillis + taskDurationInMillis
            val threeMinutesBefore = taskTime - (3 * 60 * 1000)
            val oneMinuteBefore = taskTime - (60 * 1000)
            val oneMinuteAfter = taskTime + (60 * 1000)


            taskConfigs.add(TaskTimeConfig1(taskCounts, taskTime, threeMinutesBefore, oneMinuteBefore, oneMinuteAfter))

            // ログに時間をフォーマットして出力
//            Log.d("TaskConfig", "Task #$taskNumber:")
//            Log.d("TaskConfig", "  Task Time: ${timeFormat.format(taskTime)}")
//            Log.d("TaskConfig", "  3 minutes before: ${timeFormat.format(threeMinutesBefore)}")
//            Log.d("TaskConfig", "  1 minute before: ${timeFormat.format(oneMinuteBefore)}")
//            Log.d("TaskConfig", "  1 minute after: ${timeFormat.format(oneMinuteAfter)}")
//            Log.d("TaskConfig", "---------") // 区切り
        }
    }


    // 時計の時間を更新する関数
    private fun updateTime() {
        val calendar = Calendar.getInstance()
        val currentTime = Calendar.getInstance()  // 現在の時間を取得

// 最終的な timeStatus を保持する変数（null で初期化）
        var finalTimeStatus: Int? = 0

        //ミッション1
        for (taskNumber in 0 until taskConfigs.size) {  // taskConfigs.size はタスクの数
            val taskConfig = taskConfigs[taskNumber]  // 各タスク設定を取得

            // 各タスクの詳細情報を取り出す
            val taskId = taskNumber + 1
            val taskTime = taskConfig.taskTime
            val threeMinutesBefore = taskConfig.threeMinutesBefore
            val oneMinuteBefore = taskConfig.oneMinuteBefore
            val oneMinuteAfter = taskConfig.oneMinuteAfter


            // 現在時刻が 3 分前と 1 分前の間か、1 分前と 1 分後の間かをチェック
            var timeStatus: Int? = 0

            if (currentTime.timeInMillis in (threeMinutesBefore..oneMinuteBefore)) {
                // 3 分前と 1 分前の間
                timeStatus = 1
            } else if (currentTime.timeInMillis in (oneMinuteBefore..oneMinuteAfter)) {
                // 1 分前と 1 分後の間
                timeStatus = 2
            }

            // `timeStatus` が決まったら最終結果を保存
            if (timeStatus != 0) {
                finalTimeStatus = timeStatus
            }

            // 各タスクの情報を利用して処理を行う
//            Log.d("TaskConfig", "Task #$taskId:")
//            Log.d("TaskConfig", "  Task Time: ${timeFormat.format(taskTime)}")
//            Log.d("TaskConfig", "  3 minutes before: ${timeFormat.format(threeMinutesBefore)}")
//            Log.d("TaskConfig", "  1 minute before: ${timeFormat.format(oneMinuteBefore)}")
//            Log.d("TaskConfig", "  1 minute after: ${timeFormat.format(oneMinuteAfter)}")
//            Log.d("TaskConfig", "---------")  // 区切り
        }

// ループ終了後、最終的な timeStatus を出力
        Log.d("TaskConfig", "Final Time Status: $finalTimeStatus")
//        Log.d("TaskConfig", "  current Time: ${timeFormat.format(currentTime.timeInMillis)}")

        // finalTimeStatus が決まった後に、selectedConfig を設定する
        val selectedConfig: JsonObject? = when (finalTimeStatus) {
            0 -> clockConfig.getAsJsonArray("configurations")[0].asJsonObject  // finalTimeStatus が null の場合
            1 -> clockConfig.getAsJsonArray("configurations")[1].asJsonObject    // finalTimeStatus が 1 の場合
            2 -> clockConfig.getAsJsonArray("configurations")[2].asJsonObject    // finalTimeStatus が 2 の場合
            else -> null  // finalTimeStatus がそれ以外の場合
        }

// null チェック
        selectedConfig?.let {
            updateClockDisplay(it)
        } ?: run {
            // selectedConfig が null の場合の処理（例: エラーメッセージを表示）
            Log.d("TaskConfig", "No valid configuration selected")
        }
//        updateClockDisplay(clockConfig.getAsJsonArray("configurations")[0].asJsonObject)

        // 各 TextView に時間を表示
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        hourTextView.text = String.format(Locale.getDefault(), "%02d", hour)
        minuteTextView.text = String.format(Locale.getDefault(), "%02d", minute)
        secondTextView.text = String.format(Locale.getDefault(), "%02d", second)

        // 毎秒更新
        handler.postDelayed({ updateTime() }, 1000)
    }



    private fun manualUpdateTime() {
        val calendar = Calendar.getInstance()
        val currentTime = Calendar.getInstance()  // 現在の時間を取得
        // 現在時刻が 3 分前と 1 分前の間か、1 分前と 1 分後の間かをチェック
        var timeStatus: Int? = 0
// 最終的な timeStatus を保持する変数（null で初期化）
        var finalTimeStatus: Int? = 0

        for (taskNumber in 0 until taskConfigs.size) {  // taskConfigs.size はタスクの数
            val taskConfig = taskConfigs[taskNumber]  // 各タスク設定を取得

            // 各タスクの詳細情報を取り出す
            val taskId = taskNumber + 1
            val taskTime = taskConfig.taskTime
            val threeMinutesBefore = taskConfig.threeMinutesBefore


            if (manualId == 0){
                timeStatus = 0

            }
            else if (manualId == 1) {
                if (currentTime.timeInMillis in (threeMinutesBefore..taskTime)) {
                    // 3 分前と 1 分前の間
                    timeStatus = 2
                }else {
                    timeStatus = 1
                }

            }

            if (timeStatus == 2) {
                finalTimeStatus = 2
                break // for 文を抜ける
            } else {
                finalTimeStatus = timeStatus
            }

            // 各タスクの情報を利用して処理を行う
//            Log.d("TaskConfig", "Task #$taskId:")
////            Log.d("TaskConfig", "  3 minutes before: ${timeFormat.format(currentTime)}")
//            Log.d("TaskConfig", "  3 minutes before: ${timeFormat.format(threeMinutesBefore)}")
//            Log.d("TaskConfig", "  current Time: ${timeFormat.format(currentTime.timeInMillis)}")
//            Log.d("TaskConfig", "  Task Time: ${timeFormat.format(taskTime)}")
//            Log.d("TaskConfig", "---------")  // 区切り
        }

// ループ終了後、最終的な timeStatus を出力
        Log.d("TaskConfig", "Final Time Status22: $finalTimeStatus")
//        Log.d("TaskConfig", "  current Time: ${timeFormat.format(currentTime.timeInMillis)}")
        // finalTimeStatus が決まった後に、selectedConfig を設定する
        val selectedConfig: JsonObject? = when (finalTimeStatus) {
            0 -> clockConfig.getAsJsonArray("configurations")[0].asJsonObject  // finalTimeStatus が null の場合
            1 -> clockConfig.getAsJsonArray("configurations")[1].asJsonObject    // finalTimeStatus が 1 の場合
            2 -> clockConfig.getAsJsonArray("configurations")[2].asJsonObject    // finalTimeStatus が 2 の場合
            else -> null  // finalTimeStatus がそれ以外の場合
        }

// null チェック
        selectedConfig?.let {
            updateClockDisplay(it)
        } ?: run {
            // selectedConfig が null の場合の処理（例: エラーメッセージを表示）
            Log.d("TaskConfig", "No valid configuration selected")
        }
//        updateClockDisplay(clockConfig.getAsJsonArray("configurations")[0].asJsonObject)

        // 各 TextView に時間を表示
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        hourTextView.text = String.format(Locale.getDefault(), "%02d", hour)
        minuteTextView.text = String.format(Locale.getDefault(), "%02d", minute)
        secondTextView.text = String.format(Locale.getDefault(), "%02d", second)

        // 毎秒更新
        handler.postDelayed({ manualUpdateTime() }, 1000)
    }

    private fun updateClockDisplay(config: JsonObject) {
        val hourSize = config.get("hourSize").asFloat
        val colon1Size = config.get("colon1Size").asFloat
        val minuteSize = config.get("minuteSize").asFloat
        val colon2Size = config.get("colon2Size").asFloat
        val secondSize = config.get("secondSize").asFloat

        // 時、分、秒のサイズを設定
        hourTextView.textSize = hourSize
        colon1View.textSize = colon1Size
        minuteTextView.textSize = minuteSize
        colon2View.textSize = colon2Size
        secondTextView.textSize = secondSize

        // 時の位置を設定
        val hourPosition = config.getAsJsonObject("hourPosition")
        hourTextView.x = hourPosition.get("x").asFloat
        hourTextView.y = hourPosition.get("y").asFloat

        // コロン1の位置を設定
        val colon1Position = config.getAsJsonObject("colon1Position")
        colon1View.x = colon1Position.get("x").asFloat
        colon1View.y = colon1Position.get("y").asFloat

        // 分の位置を設定
        val minutePosition = config.getAsJsonObject("minutePosition")
        minuteTextView.x = minutePosition.get("x").asFloat
        minuteTextView.y = minutePosition.get("y").asFloat

        // コロン2の位置を設定
        val colon2Position = config.getAsJsonObject("colon2Position")
        colon2View.x = colon2Position.get("x").asFloat
        colon2View.y = colon2Position.get("y").asFloat

        // 秒の位置を設定
        val secondPosition = config.getAsJsonObject("secondPosition")
        secondTextView.x = secondPosition.get("x").asFloat
        secondTextView.y = secondPosition.get("y").asFloat
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // Activity終了時にハンドラの更新を停止
    }
}
