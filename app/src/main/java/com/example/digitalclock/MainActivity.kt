package com.example.digitalclock
import java.util.Calendar
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType


class MainActivity : AppCompatActivity() {
    //機種選択
    //OPPO = 1
    //Pixel = 2
//    private var model = 1
    // デバイス情報に基づいてmodelの値を設定
    private var model = when {
        Build.MANUFACTURER.equals("oppo", ignoreCase = true) && Build.MODEL.equals(
            "CPH2199",
            ignoreCase = true
        ) -> 1

        Build.MANUFACTURER.equals("google", ignoreCase = true) && Build.MODEL.equals(
            "Pixel 7a",
            ignoreCase = true
        ) -> 2

        else -> 1 // その他の機種
    }

    //時計の表示
    private var manualId = 0

    //manualId切り替わり処理の変数
    private var tempCondition = 0

    //時計
    private lateinit var hourTextView: TextView
    private lateinit var colon1View: TextView
    private lateinit var minuteTextView: TextView
    private lateinit var colon2View: TextView
    private lateinit var secondTextView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var clockConfig: JsonObject
    private lateinit var taskConfig: JsonObject

    // ミッションの設定変数
    private var startTime: Long = 100// 初期時間（仮）
    private var missionInterval = 1 // ミッション1（仮）
    private var firstSameMission: Long = 100// 1回目の共同ミッション（仮）
    private var secondSameMission: Long = 100// 2回目の共同ミッション（仮）

    //現在時刻
    private var currentTime: Long = 0 //(仮)

    //現在の本当の時間
    private var startRealTime: Long = 0 // タップされた瞬間の時間を保存する変数

    //タスク経過時間
    private var timeLapse: Long = 0

    // startTime を解析して設定
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    //ミリ秒を"hh:mm:ss"に直す
    private fun millisecondsToTimeFormat(ms: Long): String {
        val hours = (ms / (1000 * 60 * 60)).toInt()
        val minutes = ((ms % (1000 * 60 * 60)) / (1000 * 60)).toInt()
        val seconds = ((ms % (1000 * 60)) / 1000).toInt()

        // 時間、分、秒をゼロパディングしてフォーマット
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    // クラスのプロパティとして missionCounts を定義
    private var missionCounts = 0


    //csvにまとめるリスト
    data class RecordedData(
        val time: String,
        val display: Int,
        val immersion: Int,
        val timeStamp: Int
    )

    companion object {
        const val STORAGE_PERMISSION_CODE = 101
    }

    private val recordedValues = mutableListOf<RecordedData>()


    // 値を記録する関数
    fun recordValue(time: String, display: Int, immersion: Int, timeStamp: Int) {
        val newData = RecordedData(time, display, immersion, timeStamp)
        if (touchCondition == 1) {
            recordedValues.add(newData)
            Log.d(
                "RecordedData",
                "Time: $time, display $display, Immersion: $immersion, Timestamp: $timeStamp"
            )
//            sendDataToServer(newData)
        }
    }



    // タップされた時間を記録するリスト
    private val tapTimestamps = mutableListOf<Long>()
    private var lastToTapTime = 0 //(仮)
    private var touchCondition = 0 //クリックしたら時計スタート


    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // タップイベントが発生した場合
        if (event?.action == MotionEvent.ACTION_DOWN) {
//            val hour = (currentTime / (1000 * 60 * 60)).toInt()
//            val minute = ((currentTime % (1000 * 60 * 60)) / (1000 * 60)).toInt()
//            val second = ((currentTime % (1000 * 60)) / 1000).toInt()
//            Log.d("timeStampStamp", "hour: $currentTime")
//            Log.d("timeStampStamp", "  current Time: ${millisecondsToTimeFormat(currentTime)}")
//            Log.d("timeStampStamp", "  current Time: ${millisecondsToTimeFormat(tapTime)}")
//            Log.d("hour", "minute: $minute")
//            Log.d("hour", "second: $second")

            if (touchCondition == 0) {
                touchCondition = 1 // 一回目のタップで touchCondition を 1 に設定
                // 現在の時間をミリ秒で取得
                startRealTime = Calendar.getInstance().timeInMillis
                Log.d("timeStampStamp", "startRealTime: $startRealTime")
                return super.onTouchEvent(event)
            } else {

                // 現在の時間（タイムスタンプ）を取得してリストに追加
                val tapTime = currentTime
                tapTimestamps.add(tapTime)
                val lastTwoTapTime = if (tapTimestamps.size > 1) {
                    tapTimestamps[tapTimestamps.size - 2] // 一つ前の値を取得
                } else {
                    null // リストに一つ前の値がない場合はnull
                }
                //新しくリストに追加された時に，一つ前の値との差を計算
                // lastTwoTapTimeがnullでない場合のみ計算
                lastToTapTime = if (lastTwoTapTime != null) {
                    (tapTime - lastTwoTapTime).toInt()
                } else {
                    0 // 初回タップや一つ前の値がない場合は0
                }

                // `tempCondition`の更新
                if (tempCondition == 1) {
                    //1分以内にもう一度見たら
                    if (lastToTapTime < 60 * 1000) {
                        tempCondition = 0
                    }
                } else if (tempCondition == 2) {
                    tempCondition = 1
                }

//             デバッグ用にタイムスタンプをログに表示
//            Log.d("timeStamp", "Tap timestamp: $tapTime")
//            Log.d("timeStamp", "last two timestamp: $lastTwoTapTime")
//            Log.d("timeStamp", "lastToTapTime: $lastToTapTime")
//             Log.d("timeStampStamp", "  current Time: ${millisecondsToTimeFormat(currentTime)}")
//            Log.d("timeStampStamp", "  current Time: ${millisecondsToTimeFormat(tapTime)}")
            }
        }
        return super.onTouchEvent(event)
    }

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
        val typeface = ResourcesCompat.getFont(this, R.font.digital_fonts)
        hourTextView.typeface = typeface
        colon1View.typeface = typeface
        minuteTextView.typeface = typeface
        colon2View.typeface = typeface
        secondTextView.typeface = typeface


        // JSON設定を読み込む
        taskConfig = loadJsonConfig("task_config.json")

        // 権限を確認
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 権限が無い場合はリクエスト
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }

        //タスクプロファイルを選択
        selectTaskProfile("task1") // "task1"を選択
        if (model == 1) {
            clockConfig = loadJsonConfig("clock_config_OPPO.json")
            //時計を表示
            updateTime()
        } else if (model == 2) {
            clockConfig = loadJsonConfig("clock_config_Pixel.json")
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
        val selectedTask =
            tasks.firstOrNull { it.asJsonObject.get("id").asString == taskId }?.asJsonObject
                ?: throw IllegalArgumentException("Task with id $taskId not found.")

        // 選択されたタスクの設定を読み込む
        val startTimeString = selectedTask.get("startTime").asString
        missionInterval = selectedTask.get("missionInterval").asInt
        val firstSameMissionString = selectedTask.get("firstSameMission").asString
        val secondSameMissionString = selectedTask.get("secondSameMission").asString


        // 時刻文字列をミリ秒に変換する関数
        fun timeStringToMilliseconds(timeString: String): Long {
            val timeParts = timeString.split(":")
            val hours = timeParts[0].toInt()
            val minutes = timeParts[1].toInt()
            val seconds = timeParts[2].toInt()

            // 時、分、秒をミリ秒単位に変換
            return (hours * 3600 + minutes * 60 + seconds) * 1000L
        }
        // ミリ秒に変換
        startTime = timeStringToMilliseconds(startTimeString)
        firstSameMission = timeStringToMilliseconds(firstSameMissionString)
        secondSameMission = timeStringToMilliseconds(secondSameMissionString)

        Log.d("strictTime", "startTime: $startTime")
        Log.d("strictTime", "開始時刻: ${millisecondsToTimeFormat(startTime)}")
        Log.d("strictTime", "firstSameMission: $firstSameMission")
        Log.d("strictTime", "1回目の共同ミッション: ${millisecondsToTimeFormat(firstSameMission)}")
        Log.d("strictTime", "secondSameMission: $secondSameMission")
        Log.d("strictTime", "2回目の共同ミッション: ${millisecondsToTimeFormat(secondSameMission)}")
    }

    private fun loadJsonConfig(fileName: String): JsonObject {
        // JSON ファイルを assets フォルダから読み込む
        val inputStream = assets.open(fileName)
        val reader = InputStreamReader(inputStream)
        return Gson().fromJson(reader, JsonObject::class.java).also { reader.close() }
    }

    // タスクの時間範囲と設定IDを保持するデータクラス
    data class TaskTimeConfig(
        val missionCounts: Int, // ミッションカウント
        val missionTime: Long, // タスク時間
        val firstSameMissionTime: Long, // 1回目の共同タスク時間
        val secondSameMissionTime: Long, // 2回目の共同タスク時間

    )

    // 各タスクの時間範囲をリストに保存
    val taskConfigs = mutableListOf<TaskTimeConfig>()


    // タスク範囲を事前に計算してリストに格納する関数（ミッション1）
    private fun prepareTaskConfigs() {
        missionCounts = 5  //ミッション数

        for (missionNumber in 1..missionCounts) {
            val missionDurationInMillis =
                ((missionNumber * missionInterval - 2) * 60 * 1000).toLong()
            val missionTime = startTime + missionDurationInMillis
            val firstSameMissionTime = firstSameMission
            val secondSameMissionTime = secondSameMission

            taskConfigs.add(
                TaskTimeConfig(
                    missionNumber,
                    missionTime,
                    firstSameMissionTime,
                    secondSameMissionTime
                )
            )

            // ログに時間をフォーマットして出力
            Log.d("TaskConfig", "Task #$missionNumber:")
            Log.d("TaskConfig", "  Task Time: ${millisecondsToTimeFormat(missionTime)}")
            Log.d(
                "TaskConfig",
                "  first same task: ${millisecondsToTimeFormat(firstSameMissionTime)}"
            )
            Log.d(
                "TaskConfig",
                "  second same task: ${millisecondsToTimeFormat(secondSameMissionTime)}"
            )
            Log.d("TaskConfig", "---------") // 区切り
        }
    }


    // 時計の時間を更新する関数
    private fun updateTime() {
        if (touchCondition == 1) {
            var nowRealTime = Calendar.getInstance().timeInMillis//現在の本当の時間を取得
            timeLapse = nowRealTime - startRealTime
        }

        currentTime = startTime + timeLapse  // 現在の時間を取得
//        if (touchCondition == 1) {
//            // startTime を毎秒増加させる
//            timeLapse += 1000
//        }
        // ミリ秒から時間、分、秒を計算
        val hour = (currentTime / (1000 * 60 * 60)).toInt()
        val minute = ((currentTime % (1000 * 60 * 60)) / (1000 * 60)).toInt()
        val second = ((currentTime % (1000 * 60)) / 1000).toInt()

        //常にノーマル表示
        updateClockDisplay(clockConfig.getAsJsonArray("configurations")[0].asJsonObject)
//        Log.d(
//            "timeStampStampStampStamp",
//            "  current Time: ${millisecondsToTimeFormat(currentTime)}"
//        )

        hourTextView.text = String.format(Locale.getDefault(), "%02d", hour)
        minuteTextView.text = String.format(Locale.getDefault(), "%02d", minute)
        secondTextView.text = String.format(Locale.getDefault(), "%02d", second)

        // 毎秒更新
        handler.postDelayed({ updateTime() }, 1000)
    }


    private fun manualUpdateTime() {
        if (touchCondition == 1) {
            var nowRealTime = Calendar.getInstance().timeInMillis//現在の本当の時間を取得
            timeLapse = nowRealTime - startRealTime
        }
        currentTime = startTime + timeLapse  // 現在の時間を取得
//        if (touchCondition == 1) {
//            // startTime を毎秒増加させる
//            timeLapse += 1000
//        }

        val currentSecond = ((currentTime % (1000 * 60)) / 1000).toInt() // 現在の秒を取得
        var timeStatus = 0
        var watchCondition = 0
// 最終的な timeStatus を保持する変数（null で初期化）
        var finalTimeStatus = 0
//        // 最後のタップ時刻との差を計算
        // timeDifference を null 可能な変数として初期化
        var timeDifference: Long? = null
        val lastTapTime = tapTimestamps.lastOrNull()
        //前回押された時間からの経過時間に応じて更新
        if (lastTapTime != null) {
            timeDifference = currentTime - lastTapTime
//            Log.d("timeStamp", "Time difference: $timeDifference ms")
//            Log.d("timeStamp", "last timestamp: $lastTapTime")

            //経過時間に応じてtempConditionを更新
            if (tempCondition == 0) {
                //3分見てなかったら没頭に切り替え
                if (timeDifference > 3 * 60 * 1000) {
                    tempCondition = 2
                }
            } else if (tempCondition == 1) {

                if (timeDifference > 60 * 1000) {
                    tempCondition = 2
                }
            }

//            Log.d("timeStamp", " 最後に見た時間: ${timeFormat.format(lastTapTime)}")
//            Log.d("timeStamp", "  current Time: ${timeFormat.format(currentTime)}")
//            Log.d("timeStamp", "manualId: $manualId")
            Log.d("timeStamp", "---------")  // 区切り
            watchCondition = checkTimeCondition(lastTapTime, currentTime)

        } else {
            if (touchCondition == 0) {
                Log.d("timeStamp", "not started yet.")
            } else {
                Log.d("timeStamp", "No taps recorded yet.")
            }
        }


        // tempCondition に基づいて manualId を設定
        when (tempCondition) {
            0 -> manualId = 0
            1 -> {
                if (timeDifference != null) {
                    manualId = when {
                        timeDifference < 5 * 1000 -> 1 // 5秒未満
                        else -> 0 // 10秒以上 1分未満

                    }
                }
            }

            2 -> manualId = 1
        }


        for (taskNumber in 0 until taskConfigs.size) {  // taskConfigs.size はタスクの数
            val taskConfig = taskConfigs[taskNumber]  // 各タスク設定を取得

            // 各タスクの詳細情報を取り出す
            val taskId = taskNumber + 1
            val missionTime = taskConfig.missionTime
            val firstSameMissionTime = taskConfig.firstSameMissionTime
            val secondSameMissionTime = taskConfig.secondSameMissionTime
//            Log.d("TaskConfig", "currentTime.timeInMillis$currentTime.timeInMillis")
//            Log.d("TaskConfig", "firstSameTaskTime #$firstSame:")

            if (manualId == 0) {
                if (currentTime in (missionTime - 3 * 60 * 1000..missionTime - 60 * 1000) ||
                    currentTime in (firstSameMissionTime - 3 * 60 * 1000..firstSameMissionTime - 60 * 1000) ||
                    currentTime in (secondSameMissionTime - 3 * 60 * 1000..secondSameMissionTime - 60 * 1000)
                ) {
                    // 3 分前と 1 分前の間
                    timeStatus = 1
                } else if (currentTime in (missionTime - 60 * 1000..missionTime + 60 * 1000) ||
                    currentTime in (firstSameMissionTime - 60 * 1000..firstSameMissionTime + 60 * 1000) ||
                    currentTime in (secondSameMissionTime - 60 * 1000..secondSameMissionTime + 60 * 1000)
                ) {
                    // 1 分前と 1 分後の間
                    timeStatus = 2
                }

            } else if (manualId == 1) {
                //
                if (currentTime in (missionTime - 3 * 60 * 1000..missionTime + 60 * 1000) ||
                    currentTime in (firstSameMissionTime - 3 * 60 * 1000..firstSameMissionTime + 60 * 1000) ||
                    currentTime in (secondSameMissionTime - 3 * 60 * 1000..secondSameMissionTime + 60 * 1000)
                ) {
//                if (currentTime.timeInMillis in (threeMinutesBefore..taskTime)) {
                    if (currentSecond in (0..19)) {
                        timeStatus = 3
                    } else if (currentSecond in (20..39)) {
                        timeStatus = 4
                    } else if (currentSecond in (40..59)) {
                        timeStatus = 5
                    }
                } else {
                    timeStatus = 1
                }

            }
            if (timeStatus == 2) {
                finalTimeStatus = 2
                break // for 文を抜ける
            } else if (timeStatus == 3) {
                finalTimeStatus = 3
                break // for 文を抜ける
            } else if (timeStatus == 4) {
                finalTimeStatus = 4
                break // for 文を抜ける
            } else if (timeStatus == 5) {
                finalTimeStatus = 5
                break // for 文を抜ける
            } else {
                finalTimeStatus = timeStatus
            }

        }

// ループ終了後、最終的な timeStatus を出力
//        Log.d("timeStamp", "Final Time Status: $finalTimeStatus")
        Log.d("timeStamp", "manualId: $manualId")
        Log.d("timeStamp", "tempCondition: $tempCondition")
//        Log.d("timeStamp", " 見たか判定: $watchCondition")
        Log.d("timeStamp", "---------")  // 区切り


        recordValue(
            millisecondsToTimeFormat(currentTime),
            finalTimeStatus,
            manualId,
            watchCondition
        )

        //変数の値を初期化
        watchCondition = 0

        // finalTimeStatus が決まった後に、selectedConfig を設定する
        val selectedConfig: JsonObject? = when (finalTimeStatus) {
            0 -> clockConfig.getAsJsonArray("configurations")[0].asJsonObject  // finalTimeStatus が 0 の場合
            1 -> clockConfig.getAsJsonArray("configurations")[1].asJsonObject    // finalTimeStatus が 1 の場合
            2 -> clockConfig.getAsJsonArray("configurations")[2].asJsonObject    // finalTimeStatus が 2 の場合
            3 -> clockConfig.getAsJsonArray("configurations")[3].asJsonObject  // finalTimeStatus が 3 の場合
            4 -> clockConfig.getAsJsonArray("configurations")[4].asJsonObject    // finalTimeStatus が 4 の場合
            5 -> clockConfig.getAsJsonArray("configurations")[5].asJsonObject    // finalTimeStatus が 5 の場合
            else -> null  // finalTimeStatus がそれ以外の場合
        }

// null チェック
        selectedConfig?.let {
            updateClockDisplay(it)
        } ?: run {
            // selectedConfig が null の場合の処理（例: エラーメッセージを表示）
            Log.d("TaskConfig", "No valid configuration selected")
        }
//        // ミリ秒から時間、分、秒を計算
        val hour = (currentTime / (1000 * 60 * 60)).toInt()
        val minute = ((currentTime % (1000 * 60 * 60)) / (1000 * 60)).toInt()
        val second = ((currentTime % (1000 * 60)) / 1000).toInt()



//        val hour = 13
//        val minute = 28
//        val second = 6
//        Log.d("hour", "hour: $hour")
//        Log.d("hour", "minute: $minute")
//        Log.d("hour", "second: $second")
//        Log.d(
//            "timeStampStampStampStamp",
//            "  current Time: ${millisecondsToTimeFormat(currentTime)}"
//        )
        hourTextView.text = String.format(Locale.getDefault(), "%02d", hour)
        minuteTextView.text = String.format(Locale.getDefault(), "%02d", minute)
        secondTextView.text = String.format(Locale.getDefault(), "%02d", second)

        // 毎秒更新
        handler.postDelayed({ manualUpdateTime() }, 1000)
    }

    private fun checkTimeCondition(lastTapTime: Long?, currentTime: Long): Int {
        // lastTapTimeがnullの場合は条件を満たさないので 0 を返す
        if (lastTapTime == null) return 0

//        val lastTime = Calendar.getInstance().apply {
//            timeInMillis = lastTapTime
//        }


        // 時と分が一致しているか確認
        if ((lastTapTime / (1000 * 60 * 60)).toInt() == (currentTime / (1000 * 60 * 60)).toInt() &&
            ((lastTapTime % (1000 * 60 * 60)) / (1000 * 60)).toInt() == ((currentTime % (1000 * 60 * 60)) / (1000 * 60)).toInt()
        ) {

            // 秒の差を計算して、2秒以内かどうかを確認
            val secondsDifference =
                ((currentTime % (1000 * 60)) / 1000).toInt() - ((lastTapTime % (1000 * 60)) / 1000).toInt()
            if (secondsDifference == 0) {
                return 1  // watchCondition を 1 に設定
            } else if (secondsDifference == 1) {
                return 1  // watchCondition を 1 に設定
            }
        }

        return 0  // 条件を満たさない場合は 0 を返す
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

    fun sendDataToServer(data: RecordedData) {
        val client = OkHttpClient()
        val url = "https://shigematsu.nkmr.io/clock_api/upload.php" // サーバのURL

        // JSONデータを作成
        val jsonObject = JSONObject()
        //名前の変え忘れ気をつけて
        jsonObject.put("fileName", "testtesttest.json") // ファイル名を追加
        jsonObject.put("time", data.time)
        jsonObject.put("display", data.display)
        jsonObject.put("immersion", data.immersion)
        jsonObject.put("timeStamp", data.timeStamp)


        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            jsonObject.toString()
        )

        // POSTリクエストを作成
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // サーバからの応答を処理
                    Log.d("MyApp", "Response from server: ${response.body?.string()}")
                } else {
                    Log.d("MyApp", "Failed to send data: ${response.message}")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.d("MyApp", "Error: ${e.message}")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // Activity終了時にハンドラの更新を停止
    }

}
