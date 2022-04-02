package com.iodaniel.cronetsolution

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.iodaniel.cronetsolution.data.CartData
import com.iodaniel.cronetsolution.databinding.ActivityCartBinding
import com.iodaniel.cronetsolution.util.InternetConnection
import kotlinx.coroutines.*
import java.util.*

class ActivityCart : AppCompatActivity(), OnClickListener, GrandTotalListener,
    RecyclerViewNetworkLoadingListener, DataLoadingListener {

    private val binding by lazy { ActivityCartBinding.inflate(layoutInflater) }
    private val cartAdapter = CartAdapter()
    private lateinit var cn: InternetConnection
    private lateinit var dataLoadingListener: DataLoadingListener
    private val scope = CoroutineScope(Dispatchers.IO)
    private var listOfCartItems: ArrayList<CartData> = arrayListOf()
    private val userAuth = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.cartBack.setOnClickListener(this)
        cn = InternetConnection(applicationContext)
        dataLoadingListener = this

        cn.setCustomInternetListener(object : InternetConnection.CheckInternetConnection {
            override fun isConnected() {
                binding.cartNoNetwork.visibility = View.GONE
                getCartData()
            }

            override fun notConnected() {
                runBlocking {
                    scope.launch {
                        delay(3_000)
                        runOnUiThread {
                            dataLoadingListener.notLoading()
                            binding.cartNoNetwork.visibility = View.VISIBLE
                            binding.cartNoNetwork.setOnClickListener(this@ActivityCart)
                            Snackbar.make(binding.root, "No active network", Snackbar.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            }
        })

        cartAdapter.dataset = listOfCartItems
        cartAdapter.grandTotalListener = this
        cartAdapter.recyclerViewNetworkLoadingListener = this
        binding.cartRv.adapter = cartAdapter
        binding.cartRv.layoutManager =
            LinearLayoutManager(applicationContext, LinearLayoutManager.VERTICAL, false)
    }

    private fun getCartData() {
        dataLoadingListener.dataLoading()
        val cartRef = FirebaseDatabase.getInstance().reference.child("cart").child(userAuth!!.uid)
        val cartLiveData = RealTimeDatabaseLiveData(cartRef)
        var listOfCartId: Set<String> = setOf()
        cartLiveData.observe(this) {
            dataLoadingListener.notLoading()
            if (it.value != null) {
                for ((index, data) in (it.value as HashMap<*, *>).toList().withIndex()) {
                    val jsonObj = Gson().toJson(data.second)
                    val cartProductData = Gson().fromJson(jsonObj, CartData::class.java)
                    if (cartProductData.pushId !in listOfCartId) {
                        listOfCartId = listOfCartId.plus(cartProductData.pushId)
                        listOfCartItems.add(cartProductData)
                        cartAdapter.notifyItemInserted(index)
                    }
                }
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.cart_no_network -> recreate()
            R.id.cart_back -> super.onBackPressed()
        }
    }

    override fun setGrandTotal(selectedProduct: ArrayList<CartData>) {
        var grandTotal = 0
        for (cartData in selectedProduct) {
            if (cartData.productDiscount == "") {
                grandTotal += cartData.orderQuantity.toInt() * cartData.productPrice.toInt()
            } else {
                grandTotal += cartData.orderQuantity.toInt() * cartData.productDiscount.toInt()
            }
        }

        binding.cartGrandTotal.text = grandTotal.toString()
        binding.cartProceedLayout.setOnClickListener {
            val json = Gson().toJson(selectedProduct)
            val intent = Intent(this, ActivityCheckout::class.java)
            intent.putExtra("data", json)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right_left, 0)
        }
    }

    override fun loading() {
        binding.cartProgress.visibility = View.VISIBLE
        binding.cartGrandTotalRoot.visibility = View.GONE
    }

    override fun doneLoading() {
        binding.cartProgress.visibility = View.GONE
        binding.cartGrandTotalRoot.visibility = View.VISIBLE
    }

    override fun dataLoading() {
        binding.cartDataProgressIndicator.visibility = View.VISIBLE
    }

    override fun notLoading() {
        binding.cartDataProgressIndicator.visibility = View.GONE
    }
}

interface DataLoadingListener {
    fun dataLoading()
    fun notLoading()
}


class CartAdapter : RecyclerView.Adapter<CartAdapter.ViewHolder>() {
    lateinit var grandTotalListener: GrandTotalListener
    lateinit var recyclerViewNetworkLoadingListener: RecyclerViewNetworkLoadingListener
    private val userAuth = FirebaseAuth.getInstance().currentUser
    var dataset: ArrayList<CartData> = arrayListOf()
    lateinit var context: Context

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val remove: LinearLayout = itemView.findViewById(R.id.cart_remove)
        val image: ImageView = itemView.findViewById(R.id.cart_image)
        val name: TextView = itemView.findViewById(R.id.cart_product_title)
        val quantity: TextView = itemView.findViewById(R.id.cart_quantity)
        val price: TextView = itemView.findViewById(R.id.cart_price)
        val desc: TextView = itemView.findViewById(R.id.cart_product_desc)
        val increase: CardView = itemView.findViewById(R.id.cart_increase)
        val decrease: CardView = itemView.findViewById(R.id.cart_decrease)
        val currencyTotal: TextView = itemView.findViewById(R.id.cart_currency_total)
        val currency: TextView = itemView.findViewById(R.id.cart_currency)
        val priceTotal: TextView = itemView.findViewById(R.id.cart_price_total)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.cart_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val datum = dataset[position]
        grandTotalListener.setGrandTotal(dataset)
        Glide.with(context).load(Uri.parse(datum.productImageUrl)).circleCrop().into(holder.image)
        holder.name.text = datum.productName
        holder.desc.text = datum.productDescription
        holder.price.text = datum.productPrice
        holder.priceTotal.text =
            (datum.productPrice.toInt() * datum.orderQuantity.toInt()).toString()
        holder.quantity.text = datum.orderQuantity

        holder.increase.setOnClickListener {
            var quantity = holder.quantity.text.toString().toInt()
            if (quantity == datum.productQuantity.toInt()) return@setOnClickListener else quantity += 1
            dataset[holder.adapterPosition].orderQuantity = quantity.toString()

            recyclerViewNetworkLoadingListener.loading()
            val cartRef =
                FirebaseDatabase.getInstance().reference.child("cart").child(userAuth!!.uid)
                    .child(datum.pushId)
            cartRef.setValue(datum).addOnSuccessListener {
                holder.quantity.text = quantity.toString()
                holder.priceTotal.text = (quantity * datum.productPrice.toInt()).toString()
                grandTotalListener.setGrandTotal(dataset)
                recyclerViewNetworkLoadingListener.doneLoading()
            }.addOnFailureListener {
                recyclerViewNetworkLoadingListener.doneLoading()
            }
        }
        holder.decrease.setOnClickListener {
            var quantity = holder.quantity.text.toString().toInt()
            if (quantity == 1) return@setOnClickListener else quantity -= 1
            dataset[holder.adapterPosition].orderQuantity = quantity.toString()

            recyclerViewNetworkLoadingListener.loading()
            val cartRef =
                FirebaseDatabase.getInstance().reference.child("cart").child(userAuth!!.uid)
                    .child(datum.pushId)
            cartRef.setValue(datum).addOnSuccessListener {
                holder.quantity.text = quantity.toString()
                holder.priceTotal.text = (quantity * datum.productPrice.toInt()).toString()
                grandTotalListener.setGrandTotal(dataset)
                recyclerViewNetworkLoadingListener.doneLoading()
            }.addOnFailureListener {
                recyclerViewNetworkLoadingListener.doneLoading()
            }
        }
        holder.remove.setOnClickListener {
            notifyServerRemove(datum, holder.adapterPosition)
        }
    }

    private fun notifyServerRemove(cartData: CartData, adapterPosition: Int) {
        val cartRef = FirebaseDatabase.getInstance().reference.child("cart").child(userAuth!!.uid)
            .child(cartData.pushId)
        cartRef.removeValue().addOnSuccessListener {
            notifyItemRemoved(adapterPosition)
            dataset.removeAt(adapterPosition)
            grandTotalListener.setGrandTotal(dataset)
            recyclerViewNetworkLoadingListener.doneLoading()
        }.addOnFailureListener {
            recyclerViewNetworkLoadingListener.doneLoading()
        }
    }

    override fun getItemCount(): Int = dataset.size
}

interface GrandTotalListener {
    fun setGrandTotal(selectedProduct: ArrayList<CartData>)
}

interface RecyclerViewNetworkLoadingListener {
    fun loading()
    fun doneLoading()
}