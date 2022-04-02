package com.iodaniel.cronetsolution

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.iodaniel.cronetsolution.data.CartData
import com.iodaniel.cronetsolution.databinding.ActivityCheckoutBinding
import com.iodaniel.cronetsolution.databinding.FragmentConfirmBinding
import com.iodaniel.cronetsolution.databinding.FragmentPaymentBinding
import com.iodaniel.cronetsolution.databinding.FragmentSummaryBinding
import com.iodaniel.cronetsolution.util.RoundCornerBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.util.*
import kotlin.collections.ArrayList

class ActivityCheckout : AppCompatActivity(), SelectedFragmentListener, FragmentInflateHelper {
    private val binding by lazy { ActivityCheckoutBinding.inflate(layoutInflater) }
    private lateinit var selectedFragmentListener: SelectedFragmentListener
    private lateinit var fragmentInflateHelper: FragmentInflateHelper
    private val fragmentCheckout = FragmentSummary()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        selectedFragmentListener = this
        if (intent.hasExtra("data")) {
            try {
                val selectedData: ArrayList<CartData> = arrayListOf()
                val json = intent.getStringExtra("data")
                val data: ArrayList<*> = Gson().fromJson(json, ArrayList::class.java)
                for (datum in data) {
                    val modifiedCartJson = Gson().toJson(datum)
                    val modifiedCartData: CartData =
                        Gson().fromJson(modifiedCartJson, CartData::class.java)
                    selectedData.add(modifiedCartData)
                }
                fragmentCheckout.fragmentInflateHelper = this
                val bundle = Bundle()
                bundle.putString("data", json)
                fragmentCheckout.arguments = bundle
                supportFragmentManager.beginTransaction()
                    .replace(R.id.checkout_framelayout, fragmentCheckout).commit()
            } catch (e: Exception) {
                println("Exception *********************** ${e.printStackTrace()}")
            }
        }
        /*supportFragmentManager.beginTransaction().addToBackStack("checkout")
            .setCustomAnimations(R.anim.slide_in, R.anim.fade_out)
            .replace(R.id.cart_root, fragmentCheckout).commit()*/
    }

    override fun summarySelected() {
        binding.checkoutSummary.setBackgroundResource(RoundCornerBackground.SELECTED)
        binding.checkoutPayment.setBackgroundResource(RoundCornerBackground.UNSELECTED)
        binding.checkoutConfirm.setBackgroundResource(RoundCornerBackground.UNSELECTED)
    }

    override fun paymentSelected() {
        binding.checkoutSummary.setBackgroundResource(RoundCornerBackground.UNSELECTED)
        binding.checkoutPayment.setBackgroundResource(RoundCornerBackground.SELECTED)
        binding.checkoutConfirm.setBackgroundResource(RoundCornerBackground.UNSELECTED)
    }

    override fun confirmationSelected() {
        binding.checkoutSummary.setBackgroundResource(RoundCornerBackground.UNSELECTED)
        binding.checkoutPayment.setBackgroundResource(RoundCornerBackground.UNSELECTED)
        binding.checkoutConfirm.setBackgroundResource(RoundCornerBackground.SELECTED)
    }

    override fun inflateFragment(tag: String, fragment: Fragment, bundle: Bundle) {
        fragment.arguments = bundle
        supportFragmentManager.beginTransaction().addToBackStack(tag)
            .replace(R.id.checkout_framelayout, fragment).commit()
    }
}

interface SelectedFragmentListener {
    fun summarySelected()
    fun paymentSelected()
    fun confirmationSelected()
}

interface FragmentInflateHelper {
    fun inflateFragment(tag: String, fragment: Fragment, bundle: Bundle)
}

class FragmentSummary : Fragment(), OnClickListener {
    private lateinit var binding: FragmentSummaryBinding
    private lateinit var statesLgaMap: HashMap<*, *>
    private val states: ArrayList<String> = arrayListOf()
    private lateinit var userPref: SharedPreferences
    private val lgas: HashMap<String, ArrayList<String>> = hashMapOf()
    lateinit var fragmentInflateHelper: FragmentInflateHelper
    private var json = ""
    private var productSummaryAdapter = ProductSummaryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        userPref = requireActivity().getSharedPreferences(
            getString(R.string.USER_INFO),
            Context.MODE_PRIVATE
        )
        binding = FragmentSummaryBinding.inflate(inflater, container, false)
        binding.checkoutDeliveryState.setOnClickListener(this)
        binding.checkoutDeliveryLga.setOnClickListener(this)
        binding.checkoutSummaryProceed.setOnClickListener(this)
        binding.checkoutSummaryCountryInfo.setOnClickListener(this)
        states()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val selectedData: ArrayList<CartData> = arrayListOf()
        binding.checkoutDeliveryStateText.text =
            userPref.getString(getString(R.string.USER_STATE), "")
        binding.checkoutDeliveryLgaText.text = userPref.getString(getString(R.string.USER_LGA), "")

        try {
            json = requireArguments().getString("data")!!
            val data: ArrayList<*> = Gson().fromJson(json, ArrayList::class.java)
            for (datum in data) {
                val modifiedCartJson = Gson().toJson(datum)
                val modifiedCartData: CartData =
                    Gson().fromJson(modifiedCartJson, CartData::class.java)
                selectedData.add(modifiedCartData)
            }
            productSummaryAdapter.dataset = selectedData
            binding.checkoutProductRv.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            binding.checkoutProductRv.adapter = productSummaryAdapter
        } catch (e: Exception) {
            println("Exception *********************** ${e.printStackTrace()}")
        }
    }

    private fun states() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val statesJson = requireContext().assets.open("states_and_lga.json")
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

    override fun onClick(v: View?) {
        val alertDialog = AlertDialog.Builder(requireContext())
        when (v?.id) {
            R.id.checkout_summary_proceed -> {
                val fragmentPayment = FragmentPayment()
                fragmentPayment.fragmentInflateHelper = fragmentInflateHelper
                val bundle = Bundle()
                bundle.putString("data", json)
                fragmentInflateHelper.inflateFragment("payment", fragmentPayment, bundle)
            }
            R.id.checkout_delivery_state -> {
                if (states.isNotEmpty()) {
                    val items = states.toTypedArray()
                    alertDialog.setTitle("Select state")
                    alertDialog.setItems(items) { dialog, which ->
                        binding.checkoutDeliveryStateText.text = states[which]
                        userPref.edit().putString(getString(R.string.USER_STATE), states[which])
                            .apply()
                        binding.checkoutDeliveryLgaText.text = ""
                        dialog.dismiss()
                    }.create().show()
                }
            }
            R.id.checkout_delivery_lga -> {
                val state = binding.checkoutDeliveryStateText.text.toString()
                val items = lgas[state]!!.toTypedArray()
                alertDialog.setTitle("Pick Local government")
                alertDialog.setItems(items) { dialog, which ->
                    binding.checkoutDeliveryLgaText.text = lgas[state]!![which]
                    userPref.edit().putString(getString(R.string.USER_LGA), lgas[state]!![which])
                        .apply()
                    dialog.dismiss()
                }.create().show()
            }
            R.id.checkout_summary_country_info -> {
                alertDialog.setTitle("Delivery available only in Nigeria for now")
                alertDialog.setPositiveButton("Ok") { dialog, _ ->
                    dialog.dismiss()
                }
                alertDialog.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                alertDialog.create().show()
            }
        }
    }
}

class ProductSummaryAdapter : RecyclerView.Adapter<ProductSummaryAdapter.ViewHolder>() {
    lateinit var dataset: ArrayList<CartData>
    lateinit var context: Context

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.product_summary_image)
        val name: TextView = itemView.findViewById(R.id.product_summary_title)
        val quantity: TextView = itemView.findViewById(R.id.product_summary_quantity)
        val price: TextView = itemView.findViewById(R.id.product_summary_price)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.product_summary_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        Glide.with(context).load(datum.productImageUrl).circleCrop().into(holder.image)
        holder.name.text = datum.productName
        val price = if (datum.productDiscount == "") datum.productPrice else datum.productDiscount
        holder.price.text = (price.toInt() * datum.orderQuantity.toInt()).toString()
        holder.quantity.text = datum.orderQuantity
    }

    override fun getItemCount(): Int = dataset.size
}

class FragmentPayment : Fragment(), OnClickListener {
    private lateinit var binding: FragmentPaymentBinding
    lateinit var fragmentInflateHelper: FragmentInflateHelper
    private var json = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPaymentBinding.inflate(inflater, container, false)
        binding.paymentBankTransfer.setOnClickListener(this)
        binding.paymentFlutterWave.setOnClickListener(this)
        binding.paymentGooglePay.setOnClickListener(this)
        binding.paymentPayPal.setOnClickListener(this)
        binding.paymentProceed.setOnClickListener(this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val selectedData: ArrayList<CartData> = arrayListOf()
        try {
            json = requireArguments().getString("data")!!
            val data: ArrayList<*> = Gson().fromJson(json, ArrayList::class.java)
            for (datum in data) {
                val modifiedCartJson = Gson().toJson(datum)
                val modifiedCartData: CartData =
                    Gson().fromJson(modifiedCartJson, CartData::class.java)
                selectedData.add(modifiedCartData)
            }
        } catch (e: Exception) {
            println("Exception *********************** ${e.printStackTrace()}")
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.payment_bank_transfer -> {}

            R.id.payment_pay_pal -> {}

            R.id.payment_flutter_wave -> {}

            R.id.payment_google_pay -> {}

            R.id.payment_proceed -> {
                val fragmentConfirm = FragmentConfirm()
                val bundle = Bundle()
                bundle.putString("data", json)
                fragmentInflateHelper.inflateFragment("confirm", fragmentConfirm, bundle)
            }
        }
    }
}

class FragmentConfirm : Fragment(), OnClickListener {
    private lateinit var binding: FragmentConfirmBinding
    private lateinit var userPref: SharedPreferences
    lateinit var fragmentInflateHelper: FragmentInflateHelper
    private val selectedData: ArrayList<CartData> = arrayListOf()
    private var json = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentConfirmBinding.inflate(inflater, container, false)
        userPref = requireActivity().getSharedPreferences(
            getString(R.string.USER_INFO),
            Context.MODE_PRIVATE
        )
        binding.confirmCheckout.setOnClickListener(this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var total = 0
        try {
            json = requireArguments().getString("data")!!
            val data: ArrayList<*> = Gson().fromJson(json, ArrayList::class.java)
            for (datum in data) {
                val modifiedCartJson = Gson().toJson(datum)
                val modifiedCartData: CartData =
                    Gson().fromJson(modifiedCartJson, CartData::class.java)
                if (modifiedCartData.productDiscount == "") {
                    total += (modifiedCartData.productPrice.toInt() * modifiedCartData.orderQuantity.toInt())
                } else {
                    total += (modifiedCartData.productDiscount.toInt() * modifiedCartData.orderQuantity.toInt())
                }
                selectedData.add(modifiedCartData)
            }
        } catch (e: Exception) {
            println("Exception *********************** ${e.printStackTrace()}")
        }
        binding.confirmCountry.text
        binding.confirmState.text = userPref.getString(getString(R.string.USER_STATE), "")
        binding.confirmLga.text = userPref.getString(getString(R.string.USER_LGA), "")
        binding.confirmTotal.text = total.toString()
        binding.confirmDeliveryPrice.text = "1100"
        binding.confirmSubTotal.text = (total + 1100).toString()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.confirm_checkout -> {}
        }
    }
}