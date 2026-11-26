package ua.vadovsky.cryptowatcher

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("wallets", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private lateinit var address: EditText
    private lateinit var network: Spinner
    private val networks = listOf("Bitcoin", "Ethereum", "BNB Smart Chain", "Polygon", "Solana")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12,18,32)
        window.navigationBarColor = Color.rgb(12,18,32)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,24,20,16); setBackgroundColor(Color.rgb(12,18,32)) }
        root.addView(TextView(this).apply { text="CRYPTO WATCHER PRO"; textSize=23f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(0,8,0,18) })
        root.addView(TextView(this).apply { text="Моніторинг гаманців · 5 мереж"; textSize=14f; setTextColor(Color.LTGRAY); gravity=Gravity.CENTER; setPadding(0,0,0,20) })
        network = Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,networks) }
        root.addView(network)
        address = EditText(this).apply { hint="Публічна адреса гаманця"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        root.addView(address)
        root.addView(Button(this).apply { text="ДОДАТИ ГАМАНЕЦЬ"; setOnClickListener { addWallet() } })
        root.addView(Button(this).apply { text="ОНОВИТИ БАЛАНСИ"; setOnClickListener { refreshAll() } })
        val scroll=ScrollView(this)
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,12,0,12) }
        scroll.addView(list)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(TextView(this).apply { text="Лише публічні адреси. Seed-фрази та приватні ключі не потрібні й не запитуються."; textSize=12f; setTextColor(Color.LTGRAY); gravity=Gravity.CENTER; setPadding(4,12,4,4) })
        setContentView(root); renderSaved()
    }

    private fun addWallet() {
        val a=address.text.toString().trim()
        if(a.isBlank()) { toast("Введи публічну адресу"); return }
        val key=networks[network.selectedItemPosition]+"|"+a
        val old=prefs.getStringSet("items", emptySet())!!.toMutableSet()
        old.add(key); prefs.edit().putStringSet("items",old).apply()
        address.setText(""); renderSaved(); fetch(key)
    }

    private fun renderSaved() {
        list.removeAllViews()
        prefs.getStringSet("items",emptySet())!!.sorted().forEach { key ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(12,12,12,12); setBackgroundColor(Color.rgb(25,34,53)) }
            val p=key.split("|",limit=2)
            row.addView(TextView(this).apply { text=p[0]; textSize=16f; setTextColor(Color.WHITE) })
            row.addView(TextView(this).apply { text=p.getOrElse(1){""}; textSize=12f; setTextColor(Color.LTGRAY) })
            row.addView(TextView(this).apply { tag=key; text="Баланс: натисни «Оновити баланси»"; textSize=14f; setTextColor(Color.rgb(100,220,170)); setPadding(0,8,0,4) })
            row.addView(Button(this).apply { text="Видалити"; setOnClickListener { val s=prefs.getStringSet("items",emptySet())!!.toMutableSet(); s.remove(key); prefs.edit().putStringSet("items",s).apply(); renderSaved() } })
            list.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=12 })
        }
    }

    private fun refreshAll() {
        val items=prefs.getStringSet("items",emptySet())!!.toList()
        if(items.isEmpty()) toast("Спочатку додай гаманець")
        items.forEach { fetch(it) }
    }

    private fun fetch(key:String) {
        findResult(key)?.text="Баланс: завантаження…"
        executor.execute {
            val p=key.split("|",limit=2)
            val result=try { queryBalance(p[0],p[1]) } catch(e:Exception) { "Помилка: "+(e.message ?: "мережа недоступна") }
            runOnUiThread { findResult(key)?.text="Баланс: "+result }
        }
    }

    private fun findResult(key:String):TextView? {
        for(i in 0 until list.childCount) {
            val row=list.getChildAt(i) as? LinearLayout ?: continue
            for(j in 0 until row.childCount) {
                val v=row.getChildAt(j)
                if(v is TextView && v.tag==key) return v
            }
        }
        return null
    }

    private fun queryBalance(net:String,addr:String):String = when(net) {
        "Bitcoin" -> {
            val j=JSONObject(get("https://mempool.space/api/address/"+addr))
            val c=j.getJSONObject("chain_stats"); val m=j.getJSONObject("mempool_stats")
            val sats=c.getLong("funded_txo_sum")-c.getLong("spent_txo_sum")+m.getLong("funded_txo_sum")-m.getLong("spent_txo_sum")
            "%.8f BTC".format(java.util.Locale.US,sats/100000000.0)
        }
        "Ethereum" -> rpcBalance("https://cloudflare-eth.com",addr,18,"ETH")
        "BNB Smart Chain" -> rpcBalance("https://bsc-dataseed.binance.org",addr,18,"BNB")
        "Polygon" -> rpcBalance("https://polygon-rpc.com",addr,18,"POL")
        "Solana" -> {
            val body=JSONObject().put("jsonrpc","2.0").put("id",1).put("method","getBalance").put("params",org.json.JSONArray().put(addr)).toString()
            val v=JSONObject(post("https://api.mainnet-beta.solana.com",body)).getJSONObject("result").getLong("value")
            "%.6f SOL".format(java.util.Locale.US,v/1000000000.0)
        }
        else -> "Невідома мережа"
    }

    private fun rpcBalance(endpoint:String,addr:String,decimals:Int,symbol:String):String {
        val body=JSONObject().put("jsonrpc","2.0").put("id",1).put("method","eth_getBalance").put("params",org.json.JSONArray().put(addr).put("latest")).toString()
        val hex=JSONObject(post(endpoint,body)).getString("result")
        val value=java.math.BigInteger(hex.removePrefix("0x").ifEmpty{"0"},16)
        val amount=java.math.BigDecimal(value).divide(java.math.BigDecimal.TEN.pow(decimals),8,java.math.RoundingMode.DOWN)
        return amount.toPlainString()+" "+symbol
    }

    private fun get(url:String):String {
        val c=URL(url).openConnection() as HttpURLConnection; c.connectTimeout=12000; c.readTimeout=12000
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }
    private fun post(url:String,body:String):String {
        val c=URL(url).openConnection() as HttpURLConnection; c.requestMethod="POST"; c.connectTimeout=12000; c.readTimeout=12000; c.doOutput=true; c.setRequestProperty("Content-Type","application/json")
        return try { c.outputStream.use { it.write(body.toByteArray()) }; val s=if(c.responseCode in 200..299)c.inputStream else c.errorStream; s.bufferedReader().readText() } finally { c.disconnect() }
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
