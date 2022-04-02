package com.iodaniel.cronetsolution.home_fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.iodaniel.cronetsolution.*
import com.iodaniel.cronetsolution.databinding.FragmentAccountBinding
import com.iodaniel.cronetsolution.home_fragments.admin.ActivityUpload
import com.iodaniel.cronetsolution.util.InternetConnection
import com.iodaniel.cronetsolution.util.UserRegisterListener
import com.iodaniel.cronetsolution.util.UserTypeListener
import com.iodaniel.cronetsolution.util.UserTypes

class FragmentAccount : Fragment(), OnClickListener, UserTypeListener, InformationBarListener,
    UserRegisterListener {

    private lateinit var binding: FragmentAccountBinding
    private val auth = FirebaseAuth.getInstance().currentUser
    private lateinit var cn: InternetConnection
    private lateinit var userPref: SharedPreferences
    private lateinit var userRegisterListener: UserRegisterListener
    private lateinit var informationBarListener: InformationBarListener
    private lateinit var userTypeListener: UserTypeListener

    private fun listeners() {
        binding.accountImage.setOnClickListener(this)
        binding.accountCartIcon.setOnClickListener(this)
        binding.accountName.setOnClickListener(this)
        binding.accountEmail.setOnClickListener(this)
        binding.accountLogin.setOnClickListener(this)
        binding.accountAccount.setOnClickListener(this)
        binding.accountCardDetails.setOnClickListener(this)
        binding.accountCart.setOnClickListener(this)
        binding.accountSupport.setOnClickListener(this)
        binding.accountUpload.setOnClickListener(this)
        binding.accountSignOut.setOnClickListener(this)
        userTypeListener = this
        userRegisterListener = this
        informationBarListener = this
        cn = InternetConnection(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentAccountBinding.inflate(inflater, container, false)
        userPref = requireActivity().getSharedPreferences(
            getString(R.string.USER_INFO),
            Context.MODE_PRIVATE
        )

        val userImage = userPref.getString(getString(R.string.USER_IMAGE), "")
        if (userImage != "")
            Glide.with(requireContext()).load(userImage).circleCrop().into(binding.accountImage)
        listeners()
        when (auth) {
            null -> userRegisterListener.unregistered()
            else -> userRegisterListener.registeredBuyerOrSeller()
        }
        return binding.root
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.account_image -> {
                startActivity(Intent(requireContext(), ActivityUserAccount::class.java))
                requireActivity().overridePendingTransition(0, 0)
            }
            R.id.account_cart_icon -> {
                if (auth != null) {
                    startActivity(Intent(requireContext(), ActivityCart::class.java))
                    requireActivity()
                        .overridePendingTransition(R.anim.slide_in_right_left, R.anim.fade_out)
                } else userRegisterListener.unregistered()
            }
            R.id.account_login -> {
                val fragmentSignIn = FragmentSignIn()
                fragmentSignIn.baseLayoutToReplace = R.id.home_base_root
                requireActivity().supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_right_left, R.anim.fade_out)
                    .addToBackStack("sign in").replace(R.id.home_base_root, fragmentSignIn)
                    .commit()
            }
            R.id.account_account -> {
                startActivity(Intent(requireContext(), ActivityUserAccount::class.java))
                requireActivity().overridePendingTransition(0, 0)
            }
            R.id.account_card_details -> {
                startActivity(Intent(requireContext(), ActivityCard::class.java))
                requireActivity()
                    .overridePendingTransition(R.anim.slide_in_right_left, R.anim.fade_out)
            }
            R.id.account_cart -> {
                if (auth != null) {
                    startActivity(Intent(requireContext(), ActivityCart::class.java))
                    requireActivity()
                        .overridePendingTransition(R.anim.slide_in_right_left, R.anim.fade_out)
                } else userRegisterListener.unregistered()
            }
            R.id.account_support -> {
                Snackbar.make(binding.root, "Coming soon", Snackbar.LENGTH_LONG).show()
            }
            R.id.account_sign_out -> {
                userPref.edit().clear().apply()
                FirebaseAuth.getInstance().signOut()
                requireActivity().supportFragmentManager.beginTransaction().detach(this).commit()
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.home_root, FragmentAccount()).commit()
            }
            R.id.account_upload -> {
                when (FirebaseAuth.getInstance().currentUser) {
                    null -> {
                        val fragmentSignIn = FragmentSignIn()
                        fragmentSignIn.baseLayoutToReplace = R.id.home_base_root
                        requireActivity().supportFragmentManager.beginTransaction()
                            .addToBackStack("sign in")
                            .setCustomAnimations(R.anim.slide_in_right_left, R.anim.fade_out)
                            .replace(R.id.home_base_root, fragmentSignIn).commit()
                    }
                    else -> {
                        startActivity(Intent(requireContext(), ActivityUpload::class.java))
                        requireActivity().overridePendingTransition(
                            R.anim.slide_in_right_left,
                            R.anim.fade_out
                        )
                    }
                }
            }
        }
    }

    override fun registeredBuyerOrSeller() {
        val userType = userPref.getString(getString(R.string.USER_TYPE), "")!!
        val username = userPref.getString(getString(R.string.USERNAME), "")
        binding.accountLogin.visibility = View.GONE
        binding.accountSignOut.visibility = View.VISIBLE
        binding.accountName.text = username
        binding.accountEmail.text = auth!!.email

        informationBarListener.hideInformationBar()
        when (userType) {
            UserTypes.ADMIN -> userTypeListener.admin()
            UserTypes.SELLER -> userTypeListener.seller()
            UserTypes.BUYER -> userTypeListener.buyer()
            "" -> {
            }
        }
    }

    override fun unregistered() {
        binding.accountImage.isEnabled = false
        binding.accountLogin.visibility = View.VISIBLE
        binding.accountSignOut.visibility = View.GONE
        binding.accountCartIcon.visibility = View.GONE
        binding.accountAccount.visibility = View.GONE
        binding.accountCart.visibility = View.GONE
    }

    override fun admin() {
        binding.accountImage.isEnabled = true
        println("ACCOUNT TYPE ********************************** ADMIN")
        binding.accountUpload.visibility = View.VISIBLE
        binding.accountAdmin.visibility = View.VISIBLE
        binding.accountSignOut.visibility = View.VISIBLE
        binding.accountAccount.visibility = View.VISIBLE
    }

    override fun seller() {
        println("ACCOUNT TYPE ********************************** SELLER")
        binding.accountImage.isEnabled = true
        binding.accountUpload.visibility = View.VISIBLE
        binding.accountAdmin.visibility = View.GONE
        binding.accountAccount.visibility = View.VISIBLE
    }

    override fun buyer() {
        println("ACCOUNT TYPE ********************************** BUYER")
        binding.accountImage.isEnabled = false
        binding.accountUpload.visibility = View.GONE
        binding.accountAdmin.visibility = View.GONE
        binding.accountAccount.visibility = View.GONE
        binding.accountSignOut.visibility = View.VISIBLE
    }

    override fun showInformationBar() {
        binding.accountInformationNotice.visibility = View.VISIBLE
    }

    override fun hideInformationBar() {
        binding.accountInformationNotice.visibility = View.GONE
    }
}

interface InformationBarListener {
    fun showInformationBar()
    fun hideInformationBar()
}