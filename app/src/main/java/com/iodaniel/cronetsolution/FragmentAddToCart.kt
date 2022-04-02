package com.iodaniel.cronetsolution

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.iodaniel.cronetsolution.data.CartData
import com.iodaniel.cronetsolution.databinding.FragmentAddToCartBinding
import com.iodaniel.cronetsolution.home_fragments.admin.ProductData
import com.iodaniel.cronetsolution.util.InternetConnection
import com.iodaniel.cronetsolution.util.InternetConnection.CheckInternetConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.*

class FragmentAddToCart : Fragment(), OnClickListener, CartProductDataListener {
    private lateinit var binding: FragmentAddToCartBinding
    private lateinit var pickedProductData: ProductData
    private var acceptedPrice = ""
    private lateinit var cartProductDataListener: CartProductDataListener
    private lateinit var cn: InternetConnection
    private val userAuth = FirebaseAuth.getInstance().currentUser
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentAddToCartBinding.inflate(inflater, container, false)
        binding.addToCartScrim.setOnClickListener(this)
        binding.addToCart.setOnClickListener(this)
        binding.addToCartCheckout.setOnClickListener(this)
        binding.cartIncrease.setOnClickListener(this)
        binding.cartDecrease.setOnClickListener(this)
        cartProductDataListener = this
        cn = InternetConnection(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val productJson = requireArguments().getString("product data")!!
        pickedProductData = Gson().fromJson(productJson, ProductData::class.java)

        setData()
        getCartData()
    }

    private fun setData() {
        binding.addToCartName.text = pickedProductData.productName
        Glide.with(requireContext()).load(Uri.parse(pickedProductData.productImageUrl))
            .centerInside().into(binding.addToCartImage)
        if (pickedProductData.productDiscount == "") {
            binding.addToCartPrice.text = pickedProductData.productPrice
            binding.addToCartPriceTotal.text = pickedProductData.productPrice
            acceptedPrice = pickedProductData.productPrice
        } else {
            binding.addToCartPrice.text = pickedProductData.productDiscount
            binding.addToCartPriceTotal.text = pickedProductData.productDiscount
            acceptedPrice = pickedProductData.productDiscount
        }
    }

    private fun getCartData() {
        val cartRef = FirebaseDatabase.getInstance().reference.child("cart").child(userAuth!!.uid)
        val cartLiveData = RealTimeDatabaseLiveData(cartRef)
        cartLiveData.observe(viewLifecycleOwner) {
            if (it.value != null) {
                for (data in (it.value as HashMap<*, *>).toList()) {
                    val jsonObj = Gson().toJson(data.second)
                    val cartProductData = Gson().fromJson(jsonObj, CartData::class.java)
                    if ((cartProductData.dateCreated == pickedProductData.dateCreated) && (cartProductData.sellerUid == pickedProductData.sellerUid)) {
                        cartProductDataListener.productExistsInCart(cartProductData.pushId)
                        binding.addToCartQuantity.text = cartProductData.orderQuantity
                        return@observe
                    }
                }
            }
            cartProductDataListener.productIsNewInCart()
        }
    }

    private fun setCartData(key: String = "") {
        val timeAddedToCart = Calendar.getInstance().time.time.toString()
        var cartRef = FirebaseDatabase.getInstance().reference.child("cart").child(userAuth!!.uid)
        val thisProductPushId = if (key == "") cartRef.push().key!! else key
        cartRef = cartRef.child(thisProductPushId)
        val cartData = CartData()
        cartData.dateCreated = pickedProductData.dateCreated
        cartData.dateAddedToCart = timeAddedToCart
        cartData.orderQuantity = binding.addToCartQuantity.text.toString()
        cartData.productQuantity = pickedProductData.productQuantity
        cartData.productDescription = pickedProductData.productDescription
        cartData.productDiscount = pickedProductData.productDiscount
        cartData.productImageUrl = pickedProductData.productImageUrl
        cartData.productName = pickedProductData.productName
        cartData.productPrice = pickedProductData.productPrice
        cartData.productSection = pickedProductData.productSection
        cartData.productSeo = pickedProductData.productSeo
        cartData.pushId = thisProductPushId
        cartData.sellerUid = pickedProductData.sellerUid
        cartData.buyerId = userAuth.uid
        cartData.id = pickedProductData.id

        cn.setCustomInternetListener(object : CheckInternetConnection {
            override fun isConnected() {
                binding.addToCartAddingProgress.visibility = View.VISIBLE
                uploadToFirebase(cartData, cartRef)
            }

            override fun notConnected() {
                Snackbar.make(binding.root, "No active network", Snackbar.LENGTH_LONG).show()
            }
        })
    }

    private fun uploadToFirebase(cartData: CartData, cartRef: DatabaseReference) {
        cartRef.setValue(cartData).addOnSuccessListener {
            binding.addToCartAddingProgress.visibility = View.GONE
            Snackbar.make(binding.root, "Added to Cart", Snackbar.LENGTH_LONG).show()
        }.addOnFailureListener {
            val txt = "Upload failed: ${it.localizedMessage}"
            Snackbar.make(binding.root, txt, Snackbar.LENGTH_LONG).show()
            binding.addToCartAddingProgress.visibility = View.GONE
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.add_to_cart_scrim -> requireActivity().onBackPressed()

            R.id.add_to_cart_checkout -> {
                startActivity(Intent(requireContext(), ActivityCart::class.java))
                requireActivity().overridePendingTransition(0, 0)
            }

            R.id.cart_increase -> {
                var quantity = binding.addToCartQuantity.text.toString().toInt()
                if (quantity == pickedProductData.productQuantity.toInt()) return else quantity += 1
                binding.addToCartQuantity.text = quantity.toString()
                binding.addToCartPriceTotal.text = (quantity * acceptedPrice.toInt()).toString()
            }

            R.id.cart_decrease -> {
                var quantity = binding.addToCartQuantity.text.toString().toInt()
                if (quantity == 1) return else quantity -= 1
                binding.addToCartQuantity.text = quantity.toString()
                binding.addToCartPriceTotal.text = (quantity * acceptedPrice.toInt()).toString()
            }
        }
    }

    override fun productExistsInCart(productPushId: String) {
        binding.addToCart.setOnClickListener {
            cn.setCustomInternetListener(object : CheckInternetConnection {
                override fun isConnected() {
                    setCartData(productPushId)
                }

                override fun notConnected() {
                    Snackbar.make(binding.root, "No active network", Snackbar.LENGTH_LONG).show()
                }
            })
        }
    }

    override fun productIsNewInCart() {
        setCartData()
    }
}

interface CartProductDataListener {
    fun productExistsInCart(productPushId: String)
    fun productIsNewInCart()
}