package com.iodaniel.cronetsolution

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.iodaniel.cronetsolution.databinding.ActivityUserAccountBinding
import com.iodaniel.cronetsolution.databinding.FragmentAllUploadsBinding
import com.iodaniel.cronetsolution.databinding.FragmentReturnsBinding
import com.iodaniel.cronetsolution.databinding.FragmentSoldBinding
import com.iodaniel.cronetsolution.home_fragments.admin.ActivityUpload
import com.iodaniel.cronetsolution.home_fragments.admin.ProductData
import com.iodaniel.cronetsolution.util.InternetConnection
import com.iodaniel.cronetsolution.util.InternetConnection.CheckInternetConnection
import com.iodaniel.cronetsolution.util.UserTypeData
import com.iodaniel.cronetsolution.util.UserTypes.SELLER
import com.iodaniel.cronetsolution.util.Util
import kotlinx.coroutines.*
import java.util.*

class ActivityUserAccount : AppCompatActivity(), OnClickListener, NetworkListener {
    private val binding by lazy { ActivityUserAccountBinding.inflate(layoutInflater) }
    private lateinit var userPref: SharedPreferences
    private lateinit var detailsTabAdapter: DetailsTabAdapter
    private val auth = FirebaseAuth.getInstance().currentUser
    private lateinit var cn: InternetConnection
    private lateinit var networkListener: NetworkListener
    private val scope = CoroutineScope(Dispatchers.IO)
    private var userData: UserTypeData = UserTypeData()
    private var imageUri: Uri? = null
    private var downloadImageUri = ""
    private var imageSelected: Bitmap? = null
    private var storageRef = FirebaseStorage.getInstance().reference

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
                        imageSelected = Util.scaleBitmap(dataUri!!, applicationContext)
                        binding.userAccountImage.setImageBitmap(imageSelected)
                        binding.userAccountImage.scaleType = ImageView.ScaleType.CENTER_CROP
                        uploadToDatabase()
                    }
                }
            } catch (e: Exception) {
            }
        }

    private fun uploadToDatabase() {
        val progressView = layoutInflater.inflate(R.layout.progress_layout, null, false)
        val progressText: TextView = progressView.findViewById(R.id.progress_bar_text)
        val uploadMessageDialog = AlertDialog.Builder(this)
        uploadMessageDialog.setCancelable(false)
        val contentResolver = applicationContext.contentResolver
        val mime = MimeTypeMap.getSingleton()
        val fileUri = imageUri
        val extensionType =
            mime.getExtensionFromMimeType(contentResolver?.getType(fileUri!!))!!
        val finalRef = storageRef.child("user image").child("${auth!!.uid}.$extensionType")
        val uploadTask = finalRef.putFile(fileUri!!)
        uploadMessageDialog.setView(progressView)
        val dialog = uploadMessageDialog.create()
        dialog.show()

        uploadTask.continueWith { task ->
            if (!task.isSuccessful) task.exception?.let { throw it }
            finalRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                finalRef.downloadUrl.addOnSuccessListener {
                    downloadImageUri = it.toString()
                    val userRef = FirebaseDatabase
                        .getInstance()
                        .reference
                        .child("user type")
                        .child(auth.uid)
                        .child("imageUri")
                    userRef.setValue(downloadImageUri).addOnSuccessListener {
                        progressText.text = "Upload Successful"
                        userPref.edit().putString(getString(R.string.USER_IMAGE), downloadImageUri)
                            .apply()
                        dialog.dismiss()
                    }.addOnFailureListener {
                        val txt = "Upload failed, check network connection"
                        Snackbar.make(binding.root, txt, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }.addOnFailureListener {
            progressText.text = "Upload Failed. Retry!!!"
            dialog.dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        networkListener = this
        cn = InternetConnection(applicationContext)
        userPref = getSharedPreferences(getString(R.string.USER_INFO), Context.MODE_PRIVATE)
        loadSavedData()

        networkListener.loadProgressBar()
        val netTxt = "No active internet"
        cn.setCustomInternetListener(object : CheckInternetConnection {
            override fun isConnected() {
                loadOnlineData()
                runBlocking {
                    scope.launch {
                        if (userData == null) {
                            delay(10_000)
                            if (userData == null) {
                                Snackbar.make(binding.root, netTxt, Snackbar.LENGTH_LONG).show()
                                runOnUiThread { networkListener.notLoadingProgressBar() }
                            } else runOnUiThread { networkListener.notLoadingProgressBar() }
                        } else runOnUiThread { networkListener.notLoadingProgressBar() }
                    }
                }
            }

            override fun notConnected() {
                Snackbar.make(binding.root, netTxt, Snackbar.LENGTH_LONG).show()
                runOnUiThread { networkListener.notLoadingProgressBar() }
            }
        })

        binding.userAccountImage.setOnClickListener(this)
        binding.userAccountNewUpload.setOnClickListener(this)
        binding.userAccountSignOut.setOnClickListener(this)
        binding.userAccountEdit.setOnClickListener(this)

        val tabsText = arrayListOf("All uploads", "Sold", "Returns")
        detailsTabAdapter = DetailsTabAdapter(this)
        detailsTabAdapter.dataset =
            arrayListOf(FragmentAllUploads(), FragmentSold(), FragmentReturns())
        binding.userAccountViewPager.adapter = detailsTabAdapter
        TabLayoutMediator(
            binding.userAccountTablayout,
            binding.userAccountViewPager
        ) { tabs, position ->
            tabs.text = tabsText[position]
        }.attach()
    }

    inner class DetailsTabAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        lateinit var dataset: ArrayList<Fragment>
        override fun getItemCount(): Int = dataset.size
        override fun createFragment(position: Int): Fragment {
            return dataset[position]
        }
    }

    private fun loadOnlineData() {
        if (auth != null) {
            val userRef = FirebaseDatabase
                .getInstance()
                .reference
                .child("user type")
                .child(auth.uid)
            val oneUsersLiveData = OneUsersLiveData(userRef)
            oneUsersLiveData.observe(this, { snapshot ->
                userData = snapshot

                binding.userAccountOpenOrder.text = userData.openOrders.toString()
                binding.userAccountUploads.text = userData.uploads.toString()
                binding.userAccountEmail.text = userData.email
                binding.userAccountName.text = userData.userName
                Glide.with(applicationContext).load(Uri.parse(userData.imageUri))
                    .circleCrop()
                    .into(binding.userAccountImage)
                userPref.edit().putString(getString(R.string.USER_IMAGE), userData.imageUri).apply()
            })
        }
    }

    private fun loadSavedData() {
        val name = userPref.getString(getString(R.string.USERNAME), "")
        val uploads = userPref.getString(getString(R.string.UPLOADS), "")
        val email = userPref.getString(getString(R.string.EMAIL), "")
        val image = userPref.getString(getString(R.string.IMAGE), "")

        binding.userAccountUploads.text = uploads
        binding.userAccountEmail.text = email
        binding.userAccountName.text = name
        binding.userAccountImage.setImageURI(Uri.parse(image))
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.user_account_image -> {
                val intent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
                pickFileLauncher.launch(intent)
            }
            R.id.user_account_edit -> {
                Snackbar.make(binding.root, "Product edit coming soon", Snackbar.LENGTH_LONG).show()
            }
            R.id.user_account_new_upload -> {
                val userType = userPref.getString(getString(R.string.USER_TYPE), "")
                if (userType == SELLER) {
                    when (auth) {
                        null -> {
                            val fragmentSignIn = FragmentSignIn()
                            fragmentSignIn.baseLayoutToReplace = R.id.home_base_root
                            supportFragmentManager.beginTransaction()
                                .addToBackStack("sign in")
                                .setCustomAnimations(R.anim.slide_in_right_left, R.anim.fade_out)
                                .replace(R.id.home_base_root, fragmentSignIn).commit()
                        }
                        else -> {
                            startActivity(Intent(applicationContext, ActivityUpload::class.java))
                            overridePendingTransition(
                                R.anim.slide_in_right_left,
                                R.anim.fade_out
                            )
                        }
                    }
                }
            }
            R.id.user_account_sign_out -> {}
        }
    }

    override fun loadProgressBar() {
        binding.userAccountProgress.visibility = View.VISIBLE
    }

    override fun notLoadingProgressBar() {
        binding.userAccountProgress.visibility = View.GONE
    }
}

interface NetworkListener {
    fun loadProgressBar()
    fun notLoadingProgressBar()
}

class FragmentAllUploads : Fragment() {
    private lateinit var binding: FragmentAllUploadsBinding
    private val auth = FirebaseAuth.getInstance().currentUser
    private var rvAdapter = RVAdapter()
    private var dataset: ArrayList<ProductData> = arrayListOf()
    private val allDataKeys: ArrayList<String> = arrayListOf()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAllUploadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvAdapter.dataset = dataset
        binding.userAccountAllUploadRv.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.userAccountAllUploadRv.adapter = rvAdapter
        loadAllUpload()
    }

    private fun loadAllUpload() {
        val userRef = FirebaseDatabase
            .getInstance()
            .reference
            .child("product upload")
            .child(auth!!.uid)
        val allProductUpload = AllListOfAnyLiveData(userRef)
        allProductUpload.observe(viewLifecycleOwner) {
            if (it != null) {
                for ((index, data) in it.withIndex()) {
                    val json = Gson().toJson(data)
                    val productData: ProductData = Gson().fromJson(json, ProductData::class.java)
                    val uploadKey = productData.dateCreated.toString()
                    if (uploadKey !in allDataKeys) {
                        dataset.add(productData)
                        allDataKeys.add(productData.dateCreated.toString())
                        rvAdapter.notifyItemInserted(index)
                    }
                }
            }
        }
    }

    inner class RVAdapter : RecyclerView.Adapter<RVAdapter.ViewHolder>() {
        lateinit var dataset: ArrayList<ProductData>
        lateinit var context: Context

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val image: ImageView = itemView.findViewById(R.id.account_row_image)
            val name: TextView = itemView.findViewById(R.id.account_row_name)
            val discount: TextView = itemView.findViewById(R.id.account_row_discount)
            val price: TextView = itemView.findViewById(R.id.account_row_price)
            val stroke: View = itemView.findViewById(R.id.account_row_price_stroke)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            context = parent.context
            val view = LayoutInflater.from(context).inflate(R.layout.all_uploads_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val datum = dataset[position]
            Glide.with(context).load(datum.productImageUrl).circleCrop().into(holder.image)
            holder.name.text = datum.productName
            holder.price.text = datum.productPrice
            when (datum.productDiscount) {
                "" -> {
                    holder.discount.visibility = View.GONE
                    holder.stroke.visibility = View.GONE
                }
                else -> {
                    holder.discount.visibility = View.VISIBLE
                    holder.stroke.visibility = View.VISIBLE
                    holder.discount.text = datum.productDiscount
                }
            }
        }

        override fun getItemCount(): Int = dataset.size
    }
}

class FragmentSold : Fragment() {
    private lateinit var binding: FragmentSoldBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSoldBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    inner class RVAdapter : RecyclerView.Adapter<RVAdapter.ViewHolder>() {
        lateinit var dataset: ArrayList<ProductData>
        lateinit var context: Context

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val image: ImageView = itemView.findViewById(R.id.account_row_image)
            val name: TextView = itemView.findViewById(R.id.account_row_name)
            val discount: TextView = itemView.findViewById(R.id.account_row_discount)
            val price: TextView = itemView.findViewById(R.id.account_row_price)
            val stroke: View = itemView.findViewById(R.id.account_row_price_stroke)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            context = parent.context
            val view = LayoutInflater.from(context).inflate(R.layout.all_uploads_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val datum = dataset[position]
            Glide.with(context).load(datum.productImageUrl).circleCrop().into(holder.image)
            holder.name.text = datum.productName
            holder.price.text = datum.productPrice
            when (datum.productDiscount) {
                "" -> {
                    holder.discount.visibility = View.GONE
                    holder.stroke.visibility = View.GONE
                }
                else -> {
                    holder.discount.visibility = View.VISIBLE
                    holder.stroke.visibility = View.VISIBLE
                    holder.discount.text = datum.productDiscount
                }
            }
        }

        override fun getItemCount(): Int = dataset.size
    }
}

class FragmentReturns : Fragment() {
    private lateinit var binding: FragmentReturnsBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentReturnsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    inner class RVAdapter : RecyclerView.Adapter<RVAdapter.ViewHolder>() {
        lateinit var dataset: ArrayList<ProductData>
        lateinit var context: Context

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val image: ImageView = itemView.findViewById(R.id.account_row_image)
            val name: TextView = itemView.findViewById(R.id.account_row_name)
            val discount: TextView = itemView.findViewById(R.id.account_row_discount)
            val price: TextView = itemView.findViewById(R.id.account_row_price)
            val stroke: View = itemView.findViewById(R.id.account_row_price_stroke)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            context = parent.context
            val view = LayoutInflater.from(context).inflate(R.layout.all_uploads_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val datum = dataset[position]
            Glide.with(context).load(datum.productImageUrl).circleCrop().into(holder.image)
            holder.name.text = datum.productName
            holder.price.text = datum.productPrice
            when (datum.productDiscount) {
                "" -> {
                    holder.discount.visibility = View.GONE
                    holder.stroke.visibility = View.GONE
                }
                else -> {
                    holder.discount.visibility = View.VISIBLE
                    holder.stroke.visibility = View.VISIBLE
                    holder.discount.text = datum.productDiscount
                }
            }
        }

        override fun getItemCount(): Int = dataset.size
    }
}