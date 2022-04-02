package com.iodaniel.cronetsolution

import androidx.lifecycle.LiveData
import com.google.firebase.database.*
import com.google.gson.Gson
import com.iodaniel.cronetsolution.home_fragments.admin.ProductData
import com.iodaniel.cronetsolution.util.UserTypeData

class UserTypeLiveData(private val userTypeRef: DatabaseReference) :
    LiveData<DataSnapshot>() {
    private var userTypeListener = UserTypeReferenceListener()

    override fun onActive() {
        super.onActive()
        userTypeRef.addValueEventListener(userTypeListener)
    }

    override fun onInactive() {
        super.onInactive()
        userTypeRef.removeEventListener(userTypeListener)
    }

    inner class UserTypeReferenceListener : ValueEventListener {

        override fun onDataChange(snapshot: DataSnapshot) {
            value = snapshot
        }

        override fun onCancelled(error: DatabaseError) {
        }
    }
}

class AllListOfAnyLiveData(private val allUsersRef: DatabaseReference) :
    LiveData<List<*>>() {
    private var allListener = AllUploadReferenceListener()

    override fun onActive() {
        super.onActive()
        allUsersRef.addValueEventListener(allListener)
    }

    override fun onInactive() {
        super.onInactive()
        allUsersRef.removeEventListener(allListener)
    }

    inner class AllUploadReferenceListener : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            value = (snapshot.value as HashMap<*, *>).values.toList()
        }

        override fun onCancelled(error: DatabaseError) {
        }
    }
}

class OneUsersLiveData(private val allUsersRef: DatabaseReference) :
    LiveData<UserTypeData>() {
    private var allListener = AllUploadReferenceListener()

    override fun onActive() {
        super.onActive()
        allUsersRef.addValueEventListener(allListener)
    }

    override fun onInactive() {
        super.onInactive()
        allUsersRef.removeEventListener(allListener)
    }

    inner class AllUploadReferenceListener : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
           try{
               val json = Gson().toJson(snapshot.value)
               val userTypeData: UserTypeData = Gson().fromJson(json, UserTypeData::class.java)
               value = userTypeData
           } catch (e: Exception){
               println("Exception ****************************** ${e.printStackTrace()}")
           }
        }

        override fun onCancelled(error: DatabaseError) {
        }
    }
}

class AllUploadLiveData(private val allUploadRef: DatabaseReference) :
    LiveData<DataSnapshot>() {
    private val listener = AllUploadReferenceListener()

    override fun onActive() {
        super.onActive()
        allUploadRef.addValueEventListener(listener)
    }

    override fun onInactive() {
        allUploadRef.removeEventListener(listener)
    }

    inner class AllUploadReferenceListener : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            value = snapshot
        }

        override fun onCancelled(error: DatabaseError) {
        }
    }
}

class AllUploadLiveProductData(private val allUploadRef: DatabaseReference) :
    LiveData<Pair<ArrayList<ProductData>, ArrayList<ArrayList<String>>>>() {
    private val listener = AllUploadReferenceListener()
    private val allData: ArrayList<ProductData> = arrayListOf()

    override fun onActive() {
        super.onActive()
        allUploadRef.addValueEventListener(listener)
    }

    override fun onInactive() {
        allUploadRef.removeEventListener(listener)
    }

    inner class AllUploadReferenceListener : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val userUploads = ((snapshot.value as HashMap<*, *>).values.toList())
            for (upload in userUploads) { // A USER IN USERS
                for ((index, prod) in (upload as HashMap<*, *>).values.toList().withIndex()) {
                    val product = (prod as HashMap<*, *>)
                    // val uploadKey = upload.keys.toList()[index] as String
                    val json = Gson().toJson(product)
                    val data = Gson().fromJson(json, ProductData::class.java)
                    allData.add(data)
                }
            }
            val first = allData
            val second = seoKeyWords(allData)
            value = Pair(first, second)
        }

        override fun onCancelled(error: DatabaseError) {
        }
    }

    private fun seoKeyWords(allData: ArrayList<ProductData>): ArrayList<ArrayList<String>> {
        val allSeo: ArrayList<ArrayList<String>> = arrayListOf()
        for (data in allData) allSeo.add(data.productSeo.split(",").toMutableList() as ArrayList<String>)
        return allSeo
    }
}