package com.iodaniel.cronetsolution

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.navigation.NavigationBarView.OnItemReselectedListener
import com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.iodaniel.cronetsolution.databinding.ActivityHomePageBinding
import com.iodaniel.cronetsolution.databinding.FragmentSignInBinding
import com.iodaniel.cronetsolution.databinding.FragmentSignUpBinding
import com.iodaniel.cronetsolution.home_fragments.FragmentAccount
import com.iodaniel.cronetsolution.home_fragments.FragmentMarket
import com.iodaniel.cronetsolution.home_fragments.FragmentReloadHelper
import com.iodaniel.cronetsolution.util.Keyboard.hideKeyboard
import com.iodaniel.cronetsolution.util.UserTypeData
import com.iodaniel.cronetsolution.util.UserTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.*

class HomePage : AppCompatActivity(), OnItemReselectedListener, OnItemSelectedListener,
    FragmentReloadHelper {

    private val binding by lazy { ActivityHomePageBinding.inflate(layoutInflater) }
    private lateinit var sfm: FragmentManager
    private var fragmentAccount = FragmentAccount()
    private var fragmentMarket = FragmentMarket()

    private lateinit var reloadHelper: FragmentReloadHelper
    private var userTypeRef = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance().currentUser
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.nav.setOnItemSelectedListener(this)
        binding.nav.setOnItemReselectedListener(this)
        reloadHelper = this
        sfm = supportFragmentManager
        userTypeRef = userTypeRef.child("user type")
        fragmentMarket.reloadHelper = reloadHelper
        sfm.beginTransaction().add(R.id.home_root, fragmentAccount).hide(fragmentAccount).commit()
        sfm.beginTransaction().add(R.id.home_root, fragmentMarket).hide(fragmentMarket).commit()
        sfm.beginTransaction().show(fragmentMarket).commit()
    }

    override fun onNavigationItemReselected(item: MenuItem) {
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home_menu_market -> {
                sfm.beginTransaction().hide(fragmentAccount).commit()
                sfm.beginTransaction().show(fragmentMarket).commit()
                return true
            }
            R.id.home_menu_acct -> {
                sfm.beginTransaction().hide(fragmentMarket).commit()
                sfm.beginTransaction().show(fragmentAccount).commit()
                return true
            }
        }
        return false
    }

    override fun reloadFragment() {
        if (fragmentMarket.isAdded) sfm.beginTransaction().detach(fragmentMarket).commit()
        val fragment = FragmentMarket()
        fragment.reloadHelper = this
        sfm.beginTransaction().replace(R.id.home_root, fragment, "market").commit()
    }
}

class FragmentSignIn : Fragment(), View.OnClickListener, SignInProgressListener {
    private lateinit var binding: FragmentSignInBinding
    private lateinit var userPref: SharedPreferences
    private var userTypeRef = FirebaseDatabase.getInstance().reference.child("user type")
    private var allUsers: List<*>? = null
    private lateinit var signInProgressListener: SignInProgressListener
    var baseLayoutToReplace: Int = 0
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val auth = FirebaseAuth.getInstance().currentUser

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentSignInBinding.inflate(inflater, container, false)
        signInProgressListener = this
        binding.signinButton.setOnClickListener(this)
        binding.signupCreateAcct.setOnClickListener(this)
        userPref = requireActivity().getSharedPreferences(
            getString(R.string.USER_INFO),
            Context.MODE_PRIVATE
        )
        if (auth == null) getUserType()
        return binding.root
    }

    private fun getUserType() {
        val allUsersLiveData = AllListOfAnyLiveData(userTypeRef)
        allUsersLiveData.observe(viewLifecycleOwner, { snapshot ->
            allUsers = snapshot
        })
    }

    private fun signIn() {
        hideKeyboard()
        signInProgressListener.loading()
        val email = binding.signupEmail.text.trim().toString()
        val password = binding.signupPassword.text.trim().toString()
        if (email == "" || password == "") {
            signInProgressListener.notLoading()
            return
        }
        firebaseAuth.signInWithEmailAndPassword(email, password).addOnSuccessListener {
            if (allUsers!!.isNotEmpty()) {
                for (user in allUsers!!) { //COME BACK AND HANDLE EXCEPTIONS
                    val json = Gson().toJson(user)
                    val userTypeData: UserTypeData = Gson().fromJson(json, UserTypeData::class.java)
                    if (userTypeData.email == email) {
                        userPref.edit()
                            .putString(getString(R.string.USERNAME), userTypeData.userName).apply()
                        userPref.edit()
                            .putString(getString(R.string.USER_TYPE), userTypeData.userType).apply()
                        userPref.edit()
                            .putString(getString(R.string.DATE_JOINED), userTypeData.dateJoined)
                            .apply()
                        break
                    }
                }
                signInProgressListener.notLoading()
                Snackbar.make(binding.root, "Signed in", Snackbar.LENGTH_LONG).show()
                val intent = Intent(requireContext(), HomePage::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                requireActivity().startActivity(intent)
                requireActivity().overridePendingTransition(0, 0)
            } else {
                val txt = "Network error\nHint: connect and retry"
                Snackbar.make(binding.root, txt, Snackbar.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            Snackbar.make(binding.root, it.localizedMessage, Snackbar.LENGTH_LONG).show()
            signInProgressListener.notLoading()
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.signin_button -> signIn()

            R.id.signup_create_acct -> {
                requireActivity()
                    .supportFragmentManager
                    .beginTransaction()
                    .addToBackStack("sign up")
                    .replace(baseLayoutToReplace, FragmentSignUp())
                    .commit()
            }
        }
    }

    override fun loading() {
        binding.signupProgress.visibility = View.VISIBLE
    }

    override fun notLoading() {
        binding.signupProgress.visibility = View.GONE
    }
}

interface SignInProgressListener {
    fun loading()
    fun notLoading()
}

class FragmentSignUp : Fragment(), View.OnClickListener, SignInProgressListener {
    private lateinit var binding: FragmentSignUpBinding
    private val auth = FirebaseAuth.getInstance()
    private lateinit var userPref: SharedPreferences
    private var userTypeRef = FirebaseDatabase.getInstance().reference.child("user type").push()
    private lateinit var signInProgressListener: SignInProgressListener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentSignUpBinding.inflate(inflater, container, false)
        signInProgressListener = this
        binding.signupButton.setOnClickListener(this)
        userPref = requireActivity().getSharedPreferences(
            getString(R.string.USER_INFO),
            Context.MODE_PRIVATE
        )
        return binding.root
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.signup_button -> {
                hideKeyboard()
                signInProgressListener.loading()
                var accountType = UserTypes.BUYER
                val email = binding.signinEmail.text.trim().toString()
                val password = binding.signinPassword.text.trim().toString()
                val rePassword = binding.signinRePassword.text.trim().toString()
                val username = binding.signinUsername.text.trim().toString()

                if (email == "" || password == "") {
                    signInProgressListener.notLoading()
                    return
                }
                if (rePassword != password) {
                    val txt = "Passwords don't match"
                    Snackbar.make(binding.root, txt, Snackbar.LENGTH_LONG).show()
                    signInProgressListener.notLoading()
                    return
                }
                if (username == "") {
                    val txt = "Enter username"
                    Snackbar.make(binding.root, txt, Snackbar.LENGTH_LONG).show()
                    signInProgressListener.notLoading()
                    return
                }
                if (username.length < 6) {
                    val txt = "Username should be 6 or more letters"
                    Snackbar.make(binding.root, txt, Snackbar.LENGTH_LONG).show()
                    signInProgressListener.notLoading()
                    return
                }
                if (username.split(" ").size > 1) {
                    val txt = "Username should not contain whitespace"
                    Snackbar.make(binding.root, txt, Snackbar.LENGTH_LONG).show()
                    signInProgressListener.notLoading()
                    return
                }

                when (username.split("-").last()) {
                    "Buyer" -> accountType = UserTypes.BUYER
                    "buyer" -> accountType = UserTypes.BUYER
                    "Seller" -> accountType = UserTypes.SELLER
                    "seller" -> accountType = UserTypes.SELLER
                    "SELLER" -> accountType = UserTypes.SELLER
                    "Admin" -> accountType = UserTypes.ADMIN
                    "admin" -> accountType = UserTypes.ADMIN
                    "ADMIN" -> accountType = UserTypes.ADMIN
                    else -> accountType = UserTypes.ADMIN
                }

                auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener {
                    val uid = it.user!!.uid
                    var userDetailsRef = FirebaseDatabase
                        .getInstance()
                        .reference
                        .child("all users")
                        .child(
                            it.user!!.uid
                        )
                    signInProgressListener.notLoading()
                    userDetailsRef.setValue(uid).addOnSuccessListener {
                        val datetime = Calendar.getInstance().time.time.toString()
                        val userTypeData = UserTypeData()
                        userTypeData.userType = accountType
                        userTypeData.userUID = uid
                        userTypeData.dateJoined = datetime
                        userTypeData.userName = username.split("-")[0]
                        userTypeRef.setValue(userTypeData).addOnSuccessListener {
                            logInSuccess(userTypeData)
                        }.addOnFailureListener {
                            logInFailed(it)
                        }
                    }.addOnFailureListener {
                        logInFailed(it)
                    }
                }.addOnFailureListener {
                    logInFailed(it)
                }
            }
        }
    }

    private fun logInSuccess(userTypeData: UserTypeData) {
        userPref.edit().putString(getString(R.string.USERNAME), userTypeData.userName).apply()
        userPref.edit().putString(getString(R.string.USER_TYPE), userTypeData.userType).apply()
        userPref.edit().putString(getString(R.string.DATE_JOINED), userTypeData.dateJoined).apply()

        Snackbar.make(binding.root, "Signed in", Snackbar.LENGTH_LONG).show()
        val intent = Intent(requireContext(), HomePage::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        requireActivity().startActivity(intent)
        requireActivity().overridePendingTransition(0, 0)
    }

    private fun logInFailed(it: Exception) {
        Snackbar.make(binding.root, it.localizedMessage, Snackbar.LENGTH_LONG).show()
        signInProgressListener.notLoading()
    }

    override fun loading() {
        binding.signinProgress.visibility = View.VISIBLE
    }

    override fun notLoading() {
        binding.signinProgress.visibility = View.GONE
    }
}