package com.iodaniel.cronetsolution

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayoutMediator
import com.iodaniel.cronetsolution.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : FragmentActivity(), View.OnClickListener {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val stepperImages = arrayListOf(R.drawable.pos, R.drawable.cabbage, R.drawable.roadmap)
    private val stepperColors = arrayListOf(R.color.white, R.color.yellow, R.color.appColor)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.activityMainNext.setOnClickListener(this)
        binding.activityMainSkip.setOnClickListener(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val dataValues = arrayListOf("With Cronet Solution promote, buy and sell technical or scientific products for your organization.",
            "Sell and purchase fresh Vegetables and fruits",
            "Begin")
        val adapter = ViewPagerAdapter()
        adapter.imagesDataset = stepperImages
        adapter.textDataset = dataValues
        adapter.stepperColors = stepperColors
        binding.activityMainViewPager.adapter = adapter
        TabLayoutMediator(binding.activityMainTabsLayout, binding.activityMainViewPager) { _, _ ->
        }.attach()

        ViewCompat.setOnApplyWindowInsetsListener(binding.activityMainTabsLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.activityMainTabsLayout.setPadding(0, 0, 0, insets.bottom / 2)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.activityMainSkip) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.activityMainSkip.setPadding(0, insets.top, 0, insets.top)
            WindowInsetsCompat.CONSUMED
        }
        binding.activityMainViewPager.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == stepperImages.size - 1) {
                    binding.activityMainNext.text = "Proceed"
                    binding.activityMainNext.setBackgroundResource(R.color.appColor)
                    binding.activityMainNext.setTextColor(resources.getColor(R.color.white))
                } else {
                    binding.activityMainNext.text = "Next"
                    binding.activityMainNext.setBackgroundResource(R.color.white)
                    binding.activityMainNext.setTextColor(resources.getColor(R.color.black))
                }
            }
        })
    }

    private fun goToHomePage() {
        runBlocking{
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val intent = Intent(this@MainActivity, HomePage::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                runOnUiThread { overridePendingTransition(0, 0) }
            }
        }
    }

    inner class ViewPagerAdapter : RecyclerView.Adapter<ViewPagerAdapter.ViewPagerViewHolder>() {
        var imagesDataset: ArrayList<Int> = arrayListOf()
        var textDataset: ArrayList<String> = arrayListOf()
        var stepperColors: ArrayList<Int> = arrayListOf()
        lateinit var context: Context

        inner class ViewPagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val image: ImageView = itemView.findViewById(R.id.welcome_stepper_row_image)
            val text: TextView = itemView.findViewById(R.id.welcome_stepper_row_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewPagerViewHolder {
            context = parent.context
            val view =
                LayoutInflater.from(context).inflate(R.layout.welcome_stepper_row, parent, false)
            return ViewPagerViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewPagerViewHolder, position: Int) {
            val datum = imagesDataset[position]
            val text = textDataset[position]
            val backgroundColor = stepperColors[position]
            Glide.with(context).load(datum).centerCrop().into(holder.image)
            holder.text.text = text
            holder.itemView.setBackgroundResource(backgroundColor)
        }

        override fun getItemCount(): Int = imagesDataset.size
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.activity_main_next -> {
                when (binding.activityMainNext.text) {
                    "Proceed" -> goToHomePage()
                    else -> {
                        val position = binding.activityMainViewPager.currentItem
                        if (position < stepperImages.size) binding.activityMainViewPager.setCurrentItem(
                            position + 1,
                            true
                        )
                    }
                }
            }
            R.id.activity_main_skip -> goToHomePage()
        }
    }
}