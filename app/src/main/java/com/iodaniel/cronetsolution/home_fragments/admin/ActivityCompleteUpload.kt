package com.iodaniel.cronetsolution.home_fragments.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.iodaniel.cronetsolution.R
import com.iodaniel.cronetsolution.RealTimeDatabaseLiveData
import com.iodaniel.cronetsolution.databinding.ActivityCompleteUploadBinding
import com.iodaniel.cronetsolution.util.Util
import kotlinx.coroutines.*
import java.util.*

class ActivityCompleteUpload : AppCompatActivity(), View.OnClickListener {

    private val binding by lazy { ActivityCompleteUploadBinding.inflate(layoutInflater) }
    private var productData = ProductData()
    private var upload = -1
    private var realtimeRef = FirebaseDatabase.getInstance().reference
    private var storageRef = FirebaseStorage.getInstance().reference
    private val auth = FirebaseAuth.getInstance().currentUser
    val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var userRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (intent.hasExtra("product data")) productData =
            Gson().fromJson(intent.getStringExtra("product data"), ProductData::class.java)
        realtimeRef = realtimeRef.child("product upload").child(auth!!.uid).push()
        storageRef = storageRef.child("product upload").child(auth.uid)
        userRef = FirebaseDatabase.getInstance().reference
            .child("user type")
            .child(auth.uid).child("uploads")

        binding.completeSave.setOnClickListener(this)
        binding.completeUpload.setOnClickListener(this)
        binding.completeSection.setOnClickListener(this)

        val uploadsLiveData = RealTimeDatabaseLiveData(userRef)
        uploadsLiveData.observe(this) {
            if (it != null){ upload = it.value.toString().toInt() }
        }

        runBlocking {
            scope.launch {
                binding.completeName.text = productData.productName
                binding.completePrice.text = productData.productPrice
                binding.completeQuantity.text = productData.productQuantity
                binding.completeDescription.text = productData.productDescription
                binding.completeDiscount.text = productData.productDiscount
                val imageUri = Uri.parse(productData.productImageUrl)
                val imageBitmap = Util.scaleBitmap(imageUri, applicationContext)
                runOnUiThread { binding.completeImage.setImageBitmap(imageBitmap) }
            }
        }
    }

    private fun uploadImage() {
        val progressView = layoutInflater.inflate(R.layout.progress_layout, null, false)
        val progressText: TextView = progressView.findViewById(R.id.progress_bar_text)
        val uploadMessageDialog = AlertDialog.Builder(this)
        uploadMessageDialog.setCancelable(false)
        val dateTime = Calendar.getInstance().time.time.toString()
        val contentResolver = applicationContext.contentResolver
        val mime = MimeTypeMap.getSingleton()
        val fileUri = Uri.parse(productData.productImageUrl)!!
        val extensionType =
            mime.getExtensionFromMimeType(contentResolver?.getType(fileUri))!!
        val fileName = productData.productImageUrl.split("/").last()
        val finalRef = storageRef.child("$fileName-$dateTime.$extensionType")
        val uploadTask = finalRef.putFile(fileUri)
        uploadMessageDialog.setView(progressView)
        val dialog = uploadMessageDialog.create()
        dialog.show()
        uploadTask.addOnProgressListener {
            println("Bytes transferred ********************************* ${it.bytesTransferred}")
        }
        uploadTask.continueWith { task ->
            if (!task.isSuccessful) task.exception?.let { throw it }
            finalRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                finalRef.downloadUrl.addOnSuccessListener {
                    productData.productImageUrl = it.toString()
                    if (upload == -1) runBlocking {
                        scope.launch {
                            delay(5_000)
                            if (upload == -1) return@launch
                        }
                    }
                    if (upload != -1) {
                        userRef.setValue(upload + 1).addOnSuccessListener {
                            realtimeRef.setValue(productData).addOnSuccessListener {
                                progressText.text = "Upload Successful"
                                dialog.dismiss()
                                val intent = Intent(this, ActivityUpload::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(intent)
                                overridePendingTransition(0, 0)
                            }
                        }.addOnFailureListener {

                        }
                    }
                }
            }
        }.addOnFailureListener {
            progressText.text = "Upload Failed. Retry!!!"
            dialog.dismiss()
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.complete_section -> {
                val list = arrayOf(
                    "Bakery", "Beverages", "Nonfood & Pharmacy", "Personal", "Bulk dried foods",
                    "Deli", "Prepared", "Meat", "Seafood", "Dairy", "Multicultural",
                    "Animal foods", "Toys", "Other products", "Produce", "Floral"
                )

                val alertDialog = AlertDialog.Builder(this)
                alertDialog.setItems(list) { _, which ->
                    val selected = list[which]
                    productData.productSection = selected
                    binding.completeSectionText.text = selected
                }.create().show()
            }
            R.id.complete_save -> {

            }
            R.id.complete_upload -> {
                if (binding.completeSectionText.text.trim().toString() != "") uploadImage()
            }
        }
    }
}
