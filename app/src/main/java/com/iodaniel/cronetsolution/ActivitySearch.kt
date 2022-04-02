package com.iodaniel.cronetsolution

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.iodaniel.cronetsolution.databinding.ActivitySearchBinding
import com.iodaniel.cronetsolution.databinding.FragmentSearchResultBinding
import com.iodaniel.cronetsolution.home_fragments.FragmentHelper
import com.iodaniel.cronetsolution.home_fragments.NetworkListener
import com.iodaniel.cronetsolution.home_fragments.NotificationFromRecyclerView
import com.iodaniel.cronetsolution.home_fragments.admin.ProductData
import com.iodaniel.cronetsolution.util.InternetConnection
import com.iodaniel.cronetsolution.util.InternetConnection.CheckInternetConnection
import com.iodaniel.cronetsolution.util.Keyboard.hideKeyboard
import com.iodaniel.cronetsolution.util.UserRegisterListener
import kotlinx.coroutines.*

class ActivitySearch : AppCompatActivity(), NetworkListener, OnEditorActionListener,
    OnClickListener {
    private val binding by lazy { ActivitySearchBinding.inflate(layoutInflater) }
    private var allUsers: List<*>? = null
    private val allData: ArrayList<ProductData> = arrayListOf()
    private var searchAdapter = SearchAdapter()
    private lateinit var networkListener: NetworkListener
    private val allDataKeys: ArrayList<String> = arrayListOf()
    private val seoKeys: ArrayList<String> = arrayListOf()
    private var pushIdKeys: Set<String> = setOf()
    private var datasetOfQueryData: ArrayList<ProductData> = arrayListOf()
    private var datasetOfQueryResult: ArrayList<String> = arrayListOf()
    private var allUsersRef = FirebaseDatabase.getInstance().reference.child("all users")
    private lateinit var cn: InternetConnection
    private val scope = CoroutineScope(Dispatchers.IO)

    private var productDataList: ArrayList<ProductData> = arrayListOf()
    private var seoKeywords: ArrayList<ArrayList<String>> = arrayListOf()

    private var allUsersDataRef = FirebaseDatabase.getInstance().reference.child("product upload")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        networkListener = this
        cn = InternetConnection(applicationContext)
        obtainAllData()

        addTextWatcher(binding.searchEditText)
        binding.searchEditText.setOnEditorActionListener(this)
        binding.searchCancel.setOnClickListener(this)
        searchAdapter.dataset = datasetOfQueryData
        searchAdapter.activity = this
        binding.searchRv.adapter = searchAdapter
        binding.searchRv.layoutManager =
            LinearLayoutManager(applicationContext, LinearLayoutManager.VERTICAL, false)
    }

    private fun addTextWatcher(view: EditText) {
        view.addTextChangedListener(InputValidator())
    }

    inner class InputValidator : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun afterTextChanged(s: Editable?) {
            cn.setCustomInternetListener(object : CheckInternetConnection {
                override fun isConnected() {
                    if (s.toString() != "") networkListener.networkAvailable()
                    if (productDataList.isEmpty()) {
                        runBlocking {
                            scope.launch {
                                delay(10_000) // TIMEOUT
                                val txt = "Network error"
                                if (productDataList.isEmpty()) {
                                    Snackbar.make(binding.root, txt, Snackbar.LENGTH_LONG).show()
                                   runOnUiThread { networkListener.noNetwork() }
                                }
                                else doMySearchHasData(s)
                            }
                        }
                    } else doMySearchHasData(s)
                }

                override fun notConnected() {
                    Snackbar.make(binding.root, "No active network", Snackbar.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun doMySearchHasData(query: Editable?) {
        searchAdapter.dataset = datasetOfQueryData
        binding.searchRv.adapter = searchAdapter
        binding.searchRv.layoutManager =
            LinearLayoutManager(applicationContext, LinearLayoutManager.VERTICAL, false)
        datasetOfQueryData.clear()
        datasetOfQueryResult.clear()
        runOnUiThread{
            datasetOfQueryData.clear()
            for ((index, value) in seoKeywords.withIndex()) { // ONE PRODUCT KEYWORD
                for (keyword in value) {
                    println("MATCH FOUND ********************** $keyword******* $query")
                    if (query.toString().trim() == keyword.toLowerCase(Locale.current).trim()) {
                        datasetOfQueryData.add(productDataList[index])
                        datasetOfQueryResult.add(productDataList[index].productName)
                        searchAdapter.notifyItemInserted(index)
                        break
                    }
                }
            }
            networkListener.noNetwork()
        }
    }

    private fun obtainAllData() {
        val allProductLiveData = AllUploadLiveProductData(allUsersDataRef)
        allProductLiveData.observe(this) { pair ->
            productDataList = pair.first
            seoKeywords = pair.second
        }
    }

    private fun doMySearch(query: String) {
        // WHAT I NEED FROM HERE ARE: ALL DATA,
        val allUploadsLiveData = AllUploadLiveData(allUsersDataRef)
        allUploadsLiveData.observe(this, {
            val userUploads = ((it.value as HashMap<*, *>).values.toList())
            for (upload in userUploads) { // A USER IN USERS
                for ((index, prod) in (upload as HashMap<*, *>).values.toList().withIndex()) {
                    val product = (prod as HashMap<*, *>)
                    val uploadKey = upload.keys.toList()[index] as String
                    val json = Gson().toJson(product)
                    if (uploadKey !in allDataKeys) { // AVOID DUPLICATION OF DATA
                        val data = Gson().fromJson(json, ProductData::class.java)
                        allData.add(data)
                        allDataKeys.add(uploadKey)
                        networkListener.networkAvailable()
                    }
                }
            }
            networkListener.noNetwork()
        })
    }

    override fun noNetwork() {
        binding.searchProgress.visibility = View.INVISIBLE
    }

    override fun networkAvailable() {
        binding.searchProgress.visibility = View.VISIBLE
    }

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        when (v?.id) {
            R.id.search_edit_text -> {
                val query = binding.searchEditText.text.toString().trim()
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard()
                    val fragmentSearchResult = FragmentSearchResult()
                    val json = Gson().toJson(datasetOfQueryData)
                    val bundle = Bundle()
                    bundle.putString("search data", json)
                    bundle.putString("query", query)
                    fragmentSearchResult.arguments = bundle
                    supportFragmentManager.beginTransaction().addToBackStack("search done")
                        .replace(R.id.search_root, fragmentSearchResult).commit()
                }
                return true
            }
        }
        return false
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.search_cancel -> {
                binding.searchEditText.setText("")
            }
        }
    }
}

class SearchAdapter : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {
    var dataset: ArrayList<ProductData> = arrayListOf()
    lateinit var context: Context
    lateinit var activity: Activity

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.search_text_result)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.search_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        holder.text.text = datum.productName
        holder.itemView.setOnClickListener{
            activity.hideKeyboard()
            val fragmentSearchResult = FragmentSearchResult()
            val json = Gson().toJson(dataset)
            val bundle = Bundle()
            bundle.putString("search data", json)
            bundle.putString("query", datum.productName)
            fragmentSearchResult.arguments = bundle
            (activity as AppCompatActivity).supportFragmentManager.beginTransaction()
                .addToBackStack("search done")
                .replace(R.id.search_root, fragmentSearchResult).commit()
        }
    }

    override fun getItemCount(): Int = dataset.size
}

class FragmentSearchResult : Fragment(), NotificationFromRecyclerView, FragmentHelper, UserRegisterListener {
    private lateinit var binding: FragmentSearchResultBinding
    private lateinit var userRegisterListener: UserRegisterListener
    private var searchResultDataset: ArrayList<ProductData> = arrayListOf()
    private var searchAdapter = SearchResultAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchResultBinding.inflate(inflater, container, false)
        userRegisterListener = this
        try {
            val json = requireArguments().getString("search data")
            val query = requireArguments().getString("query")
            val dataList: ArrayList<*> = Gson().fromJson(json, ArrayList::class.java)
            for (datum in dataList) {
                val dataJson = Gson().toJson(datum)
                val productData: ProductData = Gson().fromJson(dataJson, ProductData::class.java)
                searchResultDataset.add(productData)
            }
            binding.searchResultQuery.text = query
        } catch (e: Exception) {
            println("Exception *********************** ${e.printStackTrace()}")
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchAdapter.context = requireContext()
        searchAdapter.activity = requireActivity()
        searchAdapter.dataset = searchResultDataset
        searchAdapter.userRegisterListener = this
        searchAdapter.fragmentHelper = this
        searchAdapter.notificationFromRecyclerView = this
        binding.searchResultRv.adapter = searchAdapter
        binding.searchResultRv.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
    }

    override fun showNotification(message: String, actionMessage: String) {

    }

    override fun inflateFragment() {

    }

    override fun registeredBuyerOrSeller() {

    }

    override fun unregistered() {
    }

    override fun admin() {
        TODO("Not yet implemented")
    }
}

class SearchResultAdapter : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {
    lateinit var activity: Activity
    lateinit var context: Context
    private val bundle = Bundle()
    var dataset: ArrayList<ProductData> = arrayListOf()
    private val fragment = FragmentAddToCart()
    lateinit var userRegisterListener: UserRegisterListener
    lateinit var notificationFromRecyclerView: NotificationFromRecyclerView
    private val auth = FirebaseAuth.getInstance().currentUser
    lateinit var fragmentHelper: FragmentHelper

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.search_result_row_image)
        val discount: TextView = itemView.findViewById(R.id.search_result_row_discount)
        val discountRoot: LinearLayout = itemView.findViewById(R.id.search_result_row_discount_root)
        val currency: TextView = itemView.findViewById(R.id.search_result_row_currency)
        val price: TextView = itemView.findViewById(R.id.search_result_row_price)
        val name: TextView = itemView.findViewById(R.id.search_result_row_name)
        val addCart: Button = itemView.findViewById(R.id.search_result_row_add)
        val favorite: View = itemView.findViewById(R.id.search_result_row_fav)
        val priceStroke: View = itemView.findViewById(R.id.search_result_row_price_stroke)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.search_result_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        Glide.with(context).load(Uri.parse(datum.productImageUrl)).centerCrop().into(holder.image)
        holder.name.text = datum.productName
        if (datum.productDiscount != "") {
            holder.discountRoot.visibility = View.VISIBLE
            holder.discount.text = datum.productDiscount
            holder.priceStroke.visibility = View.VISIBLE
        } else {
            holder.discountRoot.visibility = View.GONE
            holder.priceStroke.visibility = View.GONE
        }
        holder.price.text = datum.productPrice

        holder.itemView.setOnClickListener {
            val json = Gson().toJson(datum)
            val intent = Intent(context, ActivityProduct::class.java)
            intent.putExtra("product data", json)
            activity.startActivity(intent)
            activity.overridePendingTransition(R.anim.slide_in_right_left, R.anim.fade_out)
        }

        holder.addCart.setOnClickListener {
            when (auth) {
                null -> userRegisterListener.unregistered()

                else -> {
                    userRegisterListener.registeredBuyerOrSeller()
                    val productJson = Gson().toJson(datum)
                    bundle.putString("product data", productJson)
                    fragment.arguments = bundle
                    (activity as AppCompatActivity).supportFragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.slide_in_right_left, R.anim.fade_out)
                        .addToBackStack("add to cart")
                        .replace(R.id.search_root, fragment).commit()
                }
            }
        }
        holder.favorite.setOnClickListener { it.setBackgroundResource(R.drawable.ic_favorited) }
    }

    override fun getItemCount(): Int = dataset.size
}