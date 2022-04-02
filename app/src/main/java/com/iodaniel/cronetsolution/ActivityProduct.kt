package com.iodaniel.cronetsolution

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.GestureDetector.OnDoubleTapListener
import android.view.GestureDetector.OnGestureListener
import android.view.View.OnTouchListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.iodaniel.cronetsolution.databinding.ActivityProductBinding
import com.iodaniel.cronetsolution.databinding.FragmentViewProductBinding
import com.iodaniel.cronetsolution.home_fragments.admin.ProductData
import com.iodaniel.cronetsolution.util.UserRegisterListener
import com.iodaniel.cronetsolution.util.UserTypes
import kotlinx.coroutines.*
import org.json.JSONArray
import java.util.*
import kotlin.collections.HashMap

class ActivityProduct : AppCompatActivity(), View.OnClickListener, OnTouchListener,
    OnGestureListener, OnDoubleTapListener, UserRegisterListener {
    private val binding by lazy { ActivityProductBinding.inflate(layoutInflater) }
    private lateinit var gestureListener: GestureDetector
    private lateinit var productData: ProductData
    private var productJson: String = ""
    private lateinit var userTypePref: SharedPreferences
    private lateinit var userRegisterListener: UserRegisterListener
    private lateinit var statesLgaMap: HashMap<*, *>
    private val states: ArrayList<String> = arrayListOf()
    private val auth = FirebaseAuth.getInstance().currentUser
    private val lgas: HashMap<String, ArrayList<String>> = hashMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        gestureListener = GestureDetector(applicationContext, this)
        binding.productCartFab.setOnClickListener(this)
        binding.productDeliveryState.setOnClickListener(this)
        binding.productDeliveryLga.setOnClickListener(this)
        binding.productImage.setOnTouchListener(this)
        binding.productAddToCart.setOnClickListener(this)
        binding.productFavorite.setOnClickListener(this)
        userRegisterListener = this
        userTypePref = getSharedPreferences(getString(R.string.USER_INFO), Context.MODE_PRIVATE)
        binding.productDeliveryStateText.text = userTypePref.getString(getString(R.string.USER_STATE), "Lagos")
        binding.productDeliveryLgaText.text = userTypePref.getString(getString(R.string.USER_LGA), "Ikeja")

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.frameLayout)
        bottomSheetBehavior.saveFlags = BottomSheetBehavior.SAVE_ALL
        states()
        if (intent.hasExtra("product data")) {
            getData()
            setData()
        }
    }

    private fun getData() {
        productJson = intent.getStringExtra("product data")!!
        productData = Gson().fromJson(productJson, ProductData::class.java)
    }

    private fun setData() {
        binding.productName.text = productData.productName
        binding.productDescription.text = productData.productDescription
        Glide.with(applicationContext).load(Uri.parse(productData.productImageUrl))
            .centerInside().into(binding.productImage)
        if (productData.productDiscount == "") {
            binding.productDiscount.text = productData.productPrice
            binding.productPriceLayout.visibility = View.GONE
            binding.productStroke.visibility = View.GONE
        } else {
            binding.productDiscount.text = productData.productDiscount
            binding.productPrice.text = productData.productPrice
        }
    }

    private fun states() {
        runBlocking {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val statesJson = applicationContext.assets.open("states_and_lga.json")
                        .bufferedReader()
                        .use { it.readText() }
                    val array = JSONArray(statesJson)
                    val jsonArray = Gson().toJson(array)
                    statesLgaMap = Gson().fromJson(jsonArray, HashMap::class.java)
                    for (obj in statesLgaMap["values"] as ArrayList<*>) {
                        val obj1 = (obj as Map<*, *>)
                        val obj2 = (obj1["nameValuePairs"] as Map<*, *>)
                        val state = obj2["state"].toString()
                        states.add(state)
                        val obj3 = obj2["lgas"] as Map<*, *>
                        lgas[state] = obj3["values"] as ArrayList<String>
                    }
                } catch (e: Exception) {
                    println("Exception ************ $${e.printStackTrace()}")
                }
            }
        }
    }

    override fun onClick(v: View?) {
        val alertDialog = AlertDialog.Builder(this)
        when (v?.id) {
            R.id.product_cart_fab -> {
                if (auth != null) {
                    startActivity(Intent(application, ActivityCart::class.java))
                    overridePendingTransition(0, 0)
                } else userRegisterListener.unregistered()
            }
            R.id.product_delivery_state -> {
                if (states.isNotEmpty()) {
                    val items = states.toTypedArray()
                    alertDialog.setTitle("Select state")
                    alertDialog.setItems(items) { dialog, which ->
                        binding.productDeliveryStateText.text = states[which]
                        userTypePref.edit().putString(getString(R.string.USER_STATE), states[which]).apply()
                        binding.productDeliveryLgaText.text = ""
                        dialog.dismiss()
                    }.create().show()
                }
            }
            R.id.product_delivery_lga -> {
                val state = binding.productDeliveryStateText.text.toString()
                val items = lgas[state]!!.toTypedArray()
                alertDialog.setTitle("Pick Local government")
                alertDialog.setItems(items) { dialog, which ->
                    binding.productDeliveryLgaText.text = lgas[state]!![which]
                    userTypePref.edit().putString(getString(R.string.USER_LGA), lgas[state]!![which]).apply()
                    dialog.dismiss()
                }.create().show()
            }
            R.id.product_favorite -> {

            }
            R.id.product_add_to_cart -> {
                if (auth != null) {
                    val userType = userTypePref.getString(getString(R.string.USER_TYPE), "")
                    if (userType == UserTypes.ADMIN) userRegisterListener.admin()
                    else userRegisterListener.registeredBuyerOrSeller()
                } else userRegisterListener.unregistered()
            }
        }
    }

    override fun registeredBuyerOrSeller() {
        val fragment = FragmentAddToCart()
        val bundle = Bundle()
        bundle.putString("product data", productJson)
        fragment.arguments = bundle
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right_left, R.anim.fade_out)
            .addToBackStack("add to cart")
            .replace(R.id.product_root, fragment).commit()
    }

    override fun unregistered() {
        binding.productMessageCenter.visibility = View.VISIBLE
        binding.productMessage.text = "You are not signed in."
        binding.productMessageAction.text = "Sign in"
        binding.productMessageAction.setOnClickListener{
            val fragmentSignIn = FragmentSignIn()
            fragmentSignIn.baseLayoutToReplace = R.id.product_root
            supportFragmentManager.beginTransaction().addToBackStack("sign in")
                .setCustomAnimations(R.anim.slide_in_right_left, R.anim.fade_out)
                .replace(R.id.product_root, fragmentSignIn)
                .commit()
        }
        runBlocking {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                delay(5_000)
                runOnUiThread { binding.productMessageCenter.visibility = View.GONE }
            }
        }
    }

    override fun admin() {
        binding.productMessageCenter.visibility = View.VISIBLE
        binding.productMessage.text = "You are an admin"
        binding.productMessageAction.text = "Admin actions"
        runBlocking {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                delay(5_000)
                runOnUiThread { binding.productMessageCenter.visibility = View.GONE }
            }
        }
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (v?.id) {
            R.id.product_image -> {
                gestureListener.onTouchEvent(event)
                gestureListener.setOnDoubleTapListener(this)
                return true
            }
        }
        return false
    }

    override fun onDown(e: MotionEvent?): Boolean {
        println("Action onDown ************************************* ")
        return false
    }

    override fun onShowPress(e: MotionEvent?) {
        println("Action onShowPress ************************************* ")
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        println("Action onSingleTapUp ************************************* ")
        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        println("Action onScroll ************************************* ")
        return false
    }

    override fun onLongPress(e: MotionEvent?) {
        println("Action onLongPress ************************************* ")
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        println("Action onFling ************************************* ")
        return false
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        return false
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        val fragmentView = FragmentViewProduct()
        val bundle = Bundle()
        bundle.putString("product data", productJson)
        fragmentView.arguments = bundle
        supportFragmentManager.beginTransaction()
            .addToBackStack("product image")
            .replace(R.id.product_root, fragmentView)
            .commit()
        return false
    }

    override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
        return false
    }
}

class FragmentViewProduct : Fragment(), OnGestureListener, OnDoubleTapListener, OnTouchListener {
    private lateinit var binding: FragmentViewProductBinding
    private lateinit var gestureDetector: GestureDetector
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentViewProductBinding.inflate(inflater, container, false)
        gestureDetector = GestureDetector(requireContext(), this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val productJson = requireArguments().getString("product data")
        val productData: ProductData = Gson().fromJson(productJson, ProductData::class.java)
        Glide.with(requireActivity()).load(productData.productImageUrl).centerInside()
            .into(binding.fragmentViewImage)
        binding.fragmentViewProductName.text = productData.productName
        binding.fragmentViewImage.setOnTouchListener(this)
    }

    override fun onDown(e: MotionEvent?): Boolean {
        println("Action onDown ************************************* ")
        return false
    }

    override fun onShowPress(e: MotionEvent?) {
        println("Action onShowPress ************************************* ")
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        println("Action onSingleTapUp ************************************* ")
        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        println("Action onScroll ************************************* ")
        return false
    }

    override fun onLongPress(e: MotionEvent?) {
        println("Action onLongPress ************************************* ")
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        println("Action onFling ************************************* ")
        return false
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        return false
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        println("Action onDoubleTap 2 ************************************* ")
        requireActivity().onBackPressed()
        return false
    }

    override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
        return false
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (v?.id) {
            R.id.fragment_view_image -> {
                gestureDetector.onTouchEvent(event)
                gestureDetector.setOnDoubleTapListener(this)
                return true
            }
        }
        return false
    }
}