package com.iodaniel.cronetsolution.home_fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.iodaniel.cronetsolution.*
import com.iodaniel.cronetsolution.databinding.FragmentMarketBinding
import com.iodaniel.cronetsolution.home_fragments.admin.ProductData
import com.iodaniel.cronetsolution.util.InternetConnection
import com.iodaniel.cronetsolution.util.UserRegisterListener
import com.iodaniel.cronetsolution.util.UserTypeListener
import com.iodaniel.cronetsolution.util.UserTypes
import kotlinx.coroutines.*
import kotlin.random.Random

class FragmentMarket : Fragment(), DataSetSizeListener, NetworkListener, OnClickListener,
    DataReadyListener, FragmentHelper, NotificationFromRecyclerView,
    HotDataListener, UserTypeListener, UserRegisterListener {
    private lateinit var binding: FragmentMarketBinding

    private var hotAdapter = HotRecyclerViewAdapter()
    private lateinit var dataReadyListener: DataReadyListener
    private lateinit var hotDataListener: HotDataListener
    lateinit var reloadHelper: FragmentReloadHelper
    private lateinit var userRegisterListener: UserRegisterListener
    private var marketAdapter = MarketRecyclerViewAdapter()
    private var recentAdapter = RecentRecyclerViewAdapter()
    private lateinit var cn: InternetConnection

    val allData: ArrayList<ProductData> = arrayListOf()
    private val allDataKeys: ArrayList<String> = arrayListOf()
    private val auth = FirebaseAuth.getInstance().currentUser
    private var userTypeRef = FirebaseDatabase.getInstance().reference.child("user type\n")
    private var allUsersRef = FirebaseDatabase.getInstance().reference.child("all users")
    private var allUsersDataRef = FirebaseDatabase.getInstance().reference.child("product upload")
    private lateinit var userTypePref: SharedPreferences
    private var allUsers: List<*>? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var networkListener: NetworkListener
    private lateinit var userTypeListener: UserTypeListener

    private var hotDataset: ArrayList<ProductData> = arrayListOf()
    private var marketDataset: ArrayList<String> = arrayListOf()
    private var recentDataset: ArrayList<ProductData> = arrayListOf()
    private lateinit var dataSetSizeListener: DataSetSizeListener

    override fun onStart() {
        super.onStart()

        cn.setCustomInternetListener(object : InternetConnection.CheckInternetConnection {
            override fun isConnected() {
                runBlocking {
                    val timeoutJob = scope.async {
                        delay(20_000L)
                        if (allData.isEmpty()) networkListener.noNetwork()
                    }
                }
            }

            override fun notConnected() {
                networkListener.noNetwork()
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        binding = FragmentMarketBinding.inflate(inflater, container, false)
        dataSetSizeListener = this
        hotDataListener = this
        userRegisterListener = this
        userTypeListener = this
        userTypePref = requireActivity().getSharedPreferences(
            getString(R.string.USER_INFO),
            Context.MODE_PRIVATE
        )
        binding.marketNoNetwork.setOnClickListener(this)
        binding.homePageToolbar.setOnClickListener(this)
        binding.marketUserIcon.setOnClickListener(this)
        cn = InternetConnection(requireContext())
        networkListener = this
        dataReadyListener = this
        binding.fragmentMarketShimmer.startShimmerAnimation()

        when (auth) {
            null -> userRegisterListener.unregistered()
            else -> userRegisterListener.registeredBuyerOrSeller()
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userImage = userTypePref.getString(getString(R.string.USER_IMAGE), "")
        if (userImage != "") Glide.with(requireContext()).load(userImage).circleCrop().into(binding.marketUserIcon)
        // MARKET RV
        marketAdapter.context = requireContext()
        marketAdapter.activity = requireActivity()
        marketAdapter.dataset = allData
        marketAdapter.fragmentHelper = this
        marketAdapter.notificationFromRecyclerView = this
        binding.homeMarketRv.adapter = marketAdapter
        binding.homeMarketRv.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        // RECENT RV
        binding.homeRecentRoot.visibility = View.VISIBLE
        recentAdapter.dataset = recentDataset
        recentAdapter.activity = requireActivity()
        binding.homeRecentRv.adapter = recentAdapter
        binding.homeRecentRv.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // HOT MARKET RV
        binding.homeHotRoot.visibility = View.VISIBLE
        hotAdapter.dataset = hotDataset
        hotAdapter.activity = requireActivity()
        binding.homeHotRv.adapter = hotAdapter
        binding.homeHotRv.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        if (auth != null && userTypePref.getString(getString(R.string.USER_TYPE), "") == "") {
            val userTypeLiveData = UserTypeLiveData(userTypeRef)
            runBlocking {
                userTypeLiveData.observe(viewLifecycleOwner) { snapshot ->
                    val listOfUsers = (snapshot.value as java.util.HashMap<*, *>?)!!.toList()
                    for (user in listOfUsers) {
                        val unIdentifiedData =
                            ((user as Pair<*, *>).second as java.util.HashMap<*, *>)
                        if (unIdentifiedData["userUID"] as String == auth.uid) {
                            userTypePref.edit().putString(
                                getString(R.string.USER_TYPE),
                                unIdentifiedData["userType"] as String?
                            ).apply()
                            this.cancel("Finished")
                            break
                        }
                    }
                }
            }
        }

        val allUploadsLiveData = AllUploadLiveData(allUsersDataRef)
        allUploadsLiveData.observe(viewLifecycleOwner, {
            runBlocking {
                scope.launch {
                    val userUploads = ((it.value as HashMap<*, *>).values.toList())
                    for (upload in userUploads) {
                        for ((index, prod) in (upload as HashMap<*, *>).values.toList()
                            .withIndex()) {
                            val product = (prod as HashMap<*, *>)
                            val uploadKey = upload.keys.toList()[index] as String
                            val json = Gson().toJson(product)
                            if (uploadKey !in allDataKeys) {
                                try {
                                    val data = Gson().fromJson(json, ProductData::class.java)
                                    allData.add(data)
                                    if (recentDataset.size < 5) recentDataset.add(data)
                                    allDataKeys.add(uploadKey)
                                    requireActivity().runOnUiThread {
                                        networkListener.networkAvailable()
                                        if (recentDataset.size < 5) recentAdapter.notifyItemInserted(recentDataset.size)
                                        marketAdapter.notifyItemInserted(allData.size)
                                        dataReadyListener.stopLoadingShimmer()
                                    }
                                } catch (e: Exception) {
                                    println("Exception *************** $${e.printStackTrace()}")
                                }
                            }
                        }
                    }
                    hotDataListener.computeHotDisplayData()
                }
            }
        })
    }

    override fun computeHotDisplayData() {
        runBlocking {
            if (hotDataset.isEmpty()) scope.launch {
                val countMax = 20
                for ((index, data) in allData.withIndex()) {
                    if (data.productDiscount == "") continue
                    if (data.productPrice < data.productDiscount) continue
                    if (hotDataset.size > countMax) break
                    hotDataset.add(data)
                    try {
                        requireActivity().runOnUiThread { hotAdapter.notifyItemInserted(index) }
                    } catch (e: Exception) {
                    } finally {
                        delay(2_000)
                        if(isAdded) requireActivity().runOnUiThread { hotAdapter.notifyItemInserted(index) }
                    }
                }
            }
        }
    }

    private fun refreshFragment() {
        reloadHelper.reloadFragment()
    }

    override fun hideRecentData() {
        binding.homeRecentRoot.visibility = View.GONE
    }

    override fun hideHotData() {
        binding.homeHotRoot.visibility = View.GONE
    }

    override fun noNetwork() {
        requireActivity().runOnUiThread {
            binding.homeRecentRoot.visibility = View.GONE
            binding.homeHotRoot.visibility = View.GONE
            binding.fragmentMarketShimmerRoot.visibility = View.GONE
            binding.fragmentMarketDataRoot.visibility = View.VISIBLE
            binding.fragmentMarketShimmer.stopShimmerAnimation()
        }
    }

    override fun networkAvailable() {
        binding.homeRecentRoot.visibility = View.VISIBLE
        binding.homeHotRoot.visibility = View.VISIBLE
        binding.marketNoNetwork.visibility = View.GONE
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.market_no_network -> refreshFragment()
            R.id.market_user_icon ->{
                startActivity(Intent(requireContext(), ActivityUserAccount::class.java))
                requireActivity().overridePendingTransition(0, 0)
            }
            R.id.home_page_toolbar -> {
                val intent = Intent(requireContext(), ActivitySearch::class.java)
                intent.action = Intent.ACTION_SEARCH
                startActivity(intent)
                requireActivity().overridePendingTransition(
                    R.anim.bottom_to_top,
                    R.anim.fade_out
                )
            }
        }
    }

    override fun dataNotReady() {
        if (isAdded) {
            requireActivity().runOnUiThread {
                binding.homeRecentRoot.visibility = View.GONE
                binding.homeHotRoot.visibility = View.GONE
                binding.fragmentMarketDataRoot.visibility = View.GONE
                binding.fragmentMarketShimmerRoot.visibility = View.VISIBLE
                binding.fragmentMarketShimmer.startShimmerAnimation()
            }
        }
    }

    override fun stopLoadingShimmer() {
        binding.homeRecentRoot.visibility = View.VISIBLE
        binding.homeHotRoot.visibility = View.VISIBLE
        binding.fragmentMarketDataRoot.visibility = View.VISIBLE
        binding.fragmentMarketShimmerRoot.visibility = View.GONE
        binding.fragmentMarketShimmer.stopShimmerAnimation()
    }

    override fun inflateFragment() {
    }

    override fun registeredBuyerOrSeller() {
        val userType = userTypePref.getString(getString(R.string.USER_TYPE), "")!!
        when (userType) {
            UserTypes.ADMIN -> userTypeListener.admin()
            UserTypes.SELLER -> userTypeListener.seller()
            UserTypes.BUYER -> userTypeListener.buyer()
            "" -> {
            }
        }
    }

    override fun unregistered() {
        binding.marketUserIcon.isEnabled = false
    }

    override fun showNotification(message: String, actionMessage: String) {
        binding.marketMessageCenter.visibility = View.VISIBLE
        binding.productMessage.text = message
        binding.productMessageAction.text = actionMessage
        binding.productMessageAction.setOnClickListener {
            val fragmentSignIn = FragmentSignIn()
            fragmentSignIn.baseLayoutToReplace = R.id.market_root
            requireActivity().supportFragmentManager.beginTransaction()
                .addToBackStack("sign in")
                .setCustomAnimations(R.anim.slide_in_right_left, R.anim.fade_out)
                .replace(R.id.home_base_root, fragmentSignIn).commit()
        }
        runBlocking {
            scope.launch {
                delay(5_000)
                try {
                    requireActivity().runOnUiThread {
                        binding.marketMessageCenter.visibility = View.GONE
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    override fun admin() {
        binding.marketUserIcon.isEnabled = true
    }

    override fun seller() {
        binding.marketUserIcon.isEnabled = true
    }

    override fun buyer() {
        binding.marketUserIcon.isEnabled = false
    }
}

interface HotDataListener {
    fun computeHotDisplayData()
}

interface NetworkListener {
    fun noNetwork()
    fun networkAvailable()
}

interface FragmentReloadHelper {
    fun reloadFragment()
}

interface DataReadyListener {
    fun dataNotReady()
    fun stopLoadingShimmer()
}

interface DataSetSizeListener {
    fun hideRecentData()
    fun hideHotData()
}

class HotRecyclerViewAdapter : RecyclerView.Adapter<HotRecyclerViewAdapter.ViewHolder>() {
    var dataset: ArrayList<ProductData> = arrayListOf()
    lateinit var context: Context
    lateinit var activity: Activity

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.hot_row_image)
        val discount: TextView = itemView.findViewById(R.id.home_hot_row_discount)
        val discountRoot: LinearLayout = itemView.findViewById(R.id.home_hot_row_discount_root)

        // val currency: TextView = itemView.findViewById(R.id.home_hot_row_currency)
        // val stroke: View = itemView.findViewById(R.id.home_hot_row_price_stroke)
        // val price: TextView = itemView.findViewById(R.id.home_hot_row_price)
        val name: TextView = itemView.findViewById(R.id.home_hot_row_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.home_hot_row, null, false)
        return ViewHolder((view))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        Glide.with(context).load(Uri.parse(datum.productImageUrl)).centerCrop()
            .into(holder.image)
        holder.name.text = datum.productName
        holder.discount.text =
            if (datum.productDiscount != "") datum.productDiscount else datum.productPrice

        holder.itemView.setOnClickListener {
            val json = Gson().toJson(datum)
            val intent = Intent(context, ActivityProduct::class.java)
            intent.putExtra("product data", json)
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)
        }
    }

    override fun getItemCount(): Int = dataset.size
}

class RecentRecyclerViewAdapter : RecyclerView.Adapter<RecentRecyclerViewAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.recent_row_image)
        val discount: TextView = itemView.findViewById(R.id.home_recent_row_discount)
        val currency: TextView = itemView.findViewById(R.id.home_recent_row_currency)
        val name: TextView = itemView.findViewById(R.id.home_recent_row_name)
    }

    var dataset: ArrayList<ProductData> = arrayListOf()
    lateinit var context: Context
    lateinit var activity: Activity

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.home_recent_row, null, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        Glide.with(context).load(Uri.parse(datum.productImageUrl)).centerCrop()
            .into(holder.image)
        holder.name.text = datum.productName
        holder.discount.text =
            if (datum.productDiscount == "") datum.productPrice else datum.productDiscount

        holder.itemView.setOnClickListener {
            val json = Gson().toJson(datum)
            val intent = Intent(context, ActivityProduct::class.java)
            intent.putExtra("product data", json)
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)
        }
    }

    override fun getItemCount(): Int = dataset.size
}

class MarketRecyclerViewAdapter : RecyclerView.Adapter<MarketRecyclerViewAdapter.ViewHolder>(),
    UserRegisterListener {

    private val bundle = Bundle()
    lateinit var context: Context
    lateinit var activity: Activity
    private val fragment = FragmentAddToCart()
    private lateinit var userRegisterListener: UserRegisterListener
    lateinit var notificationFromRecyclerView: NotificationFromRecyclerView
    private val auth = FirebaseAuth.getInstance().currentUser
    lateinit var fragmentHelper: FragmentHelper
    var dataset: ArrayList<ProductData> = arrayListOf()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.market_row_image)
        val discount: TextView = itemView.findViewById(R.id.home_market_row_discount)
        val discountRoot: LinearLayout =
            itemView.findViewById(R.id.home_market_row_discount_root)
        val currency: TextView = itemView.findViewById(R.id.home_market_row_currency)
        val price: TextView = itemView.findViewById(R.id.home_market_row_price)
        val name: TextView = itemView.findViewById(R.id.home_market_row_name)
        val addCart: Button = itemView.findViewById(R.id.home_market_row_add)
        val favorite: View = itemView.findViewById(R.id.home_market_row_fav)
        val priceStroke: View = itemView.findViewById(R.id.home_market_row_price_stroke)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        userRegisterListener = this
        val view = LayoutInflater.from(context).inflate(R.layout.home_market_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        Glide.with(context).load(Uri.parse(datum.productImageUrl)).centerCrop()
            .into(holder.image)
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
            activity.overridePendingTransition(0, 0)
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
                        .addToBackStack("add to cart")
                        .setCustomAnimations(R.anim.slide_in_right_left, R.anim.fade_out)
                        .replace(R.id.market_root, fragment).commit()
                }
            }
        }
        holder.favorite.setOnClickListener { it.setBackgroundResource(R.drawable.ic_favorited) }
    }

    override fun getItemCount(): Int = dataset.size
    override fun registeredBuyerOrSeller() {
    }

    override fun unregistered() {
        notificationFromRecyclerView.showNotification("Sign in to add to cart", "Sign in")
    }

    override fun admin() {
    }
}

interface FragmentHelper {
    fun inflateFragment()
}

interface NotificationFromRecyclerView {
    fun showNotification(message: String, actionMessage: String)
}