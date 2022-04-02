package com.iodaniel.cronetsolution.home_fragments.admin

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.iodaniel.cronetsolution.R
import com.iodaniel.cronetsolution.databinding.ActivityUploadBinding
import com.iodaniel.cronetsolution.util.Util.scaleBitmap
import java.util.*

class ActivityUpload : AppCompatActivity(), View.OnClickListener {
    private val binding by lazy { ActivityUploadBinding.inflate(layoutInflater) }
    private var productData = ProductData()
    private var imageSelected: Bitmap? = null
    private var imageUri: Uri? = null

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            try {
                if (it.resultCode == RESULT_OK && it.data!!.data != null) {
                    val dataUri = it.data!!.data
                    imageUri = it.data!!.data
                    val contentResolver = applicationContext.contentResolver
                    val mime = MimeTypeMap.getSingleton()
                    val extensionType =
                        mime.getExtensionFromMimeType(contentResolver?.getType(dataUri!!))!!
                    val conditions = extensionType ==
                            "jpg" || extensionType == "jpeg" || extensionType == "png" || extensionType == "PNG" || extensionType == "JPG"
                    if (conditions) {
                        imageSelected = scaleBitmap(dataUri!!, applicationContext)
                        binding.uploadProductImage.setImageBitmap(imageSelected)
                        binding.uploadProductImage.scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                }
            } catch (e: Exception) {
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.uploadSeoInfo.setOnClickListener(this)
        binding.uploadProductImage.setOnClickListener(this)
        binding.uploadProductImageCancel.setOnClickListener(this)
        binding.uploadProceed.setOnClickListener(this)
    }

    private fun validateInput(): ProductData {
        val productName = binding.uploadProductName.text.trim().toString()
        val productDescription = binding.uploadProductDescription.text.trim().toString()
        val productPrice = binding.uploadProductPrice.text.trim().toString()
        val productQuantity = binding.uploadProductQuantity.text.trim().toString()
        val productDiscount = binding.uploadProductDiscount.text.trim().toString()
        val productSeo = binding.uploadProductSeo.text.trim().toString()
        val dateCreated = Calendar.getInstance().time.time;toString()
        if (productName == "" || productDescription == "" || productPrice == "" || productQuantity == "" || imageSelected == null) return ProductData()
        productData.productName = productName
        productData.productDescription = productDescription
        productData.productPrice = productPrice
        productData.productQuantity = productQuantity
        productData.productDiscount = productDiscount
        productData.productSeo = productSeo
        productData.dateCreated = dateCreated

        //Decode Bitmap
        productData.productImageUrl = imageUri!!.toString()
        return productData
    }

    private fun imageFun() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
        pickFileLauncher.launch(intent)
    }

    private fun proceed() {
        if (validateInput().productName == "") return
        val json = Gson().toJson(productData)
        val intent = Intent(this, ActivityCompleteUpload::class.java)
        intent.putExtra("product data", json)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    override fun onBackPressed() {
        if (validateInput().productName == "") super.onBackPressed() else {
            val alertDialog = AlertDialog.Builder(this)
            alertDialog.setTitle("Stop editing?")
            alertDialog.setItems(arrayOf("Keep editing", "Discard")) { dialog, which ->
                when (which) {
                    0 -> dialog.dismiss()
                    1 -> super.onBackPressed()
                }
            }.create().show()
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.upload_seo_info -> {
                try {
                    val view = layoutInflater.inflate(R.layout.popup_info, null)
                    val popupInfo = AlertDialog.Builder(this)
                    popupInfo.setView(view)
                    val text =
                        "Search engine optimization. \nEnter key words that will improve product market."
                    val infoTextView: TextView = view.findViewById(R.id.upload_pop_up_info_text)
                    infoTextView.text = text
                    popupInfo.create().show()
                } catch (e: Exception) {
                }
            }
            R.id.upload_product_image -> imageFun()

            R.id.upload_proceed -> proceed()

            R.id.upload_product_image_cancel -> {
                imageSelected = null
                binding.uploadProductImage.setImageResource(R.drawable.upload_thumbnail)
                binding.uploadProductImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
                Snackbar.make(binding.root, "Image cleared", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
}