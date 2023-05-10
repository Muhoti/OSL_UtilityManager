package ke.co.osl.utcollectorapp

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import ke.co.osl.utcollectorapp.api.ApiInterface
import ke.co.osl.utcollectorapp.models.Message
import retrofit2.*
import android.util.Patterns
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import ke.co.osl.utcollectorapp.models.LoginBody
import ke.co.osl.utcollectorapp.models.RecoverPasswordBody
import ke.co.osl.utcollectorapp.models.RegBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class PublicLoginPage: AppCompatActivity() {
    lateinit var fpdialog: Dialog
    lateinit var regdialog: Dialog
    lateinit var preferences: SharedPreferences
    lateinit var editor: SharedPreferences.Editor
    lateinit var forgot: TextView
    lateinit var register: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publiclogin)

        register = findViewById(R.id.register)
        forgot = findViewById(R.id.forgotPassword)

        forgot.setOnClickListener {
            showDialog()
        }

        register.setOnClickListener {
            showRegDialog()
        }

//        Theme_Black_NoTitleBar_Fullscreen - style theme changed from...
        fpdialog = Dialog(this)
        fpdialog.setContentView(R.layout.custom_dialog)
        regdialog = Dialog(this)
        regdialog.setContentView(R.layout.reg_dialog)

        preferences = this.getSharedPreferences("ut_manager", MODE_PRIVATE)
        editor = preferences.edit()
        postLoginDetails()
    }

    private fun showDialog() {
        getNewPassword(fpdialog)
        fpdialog.getWindow()?.setBackgroundDrawableResource(R.drawable.background_transparent);
        fpdialog.show()
    }

    private fun showRegDialog() {
        registerUser(regdialog)
        regdialog.getWindow()?.setBackgroundDrawableResource(R.drawable.background_transparent);
        regdialog.show()
    }

    private fun postLoginDetails(){
        val next = findViewById<Button>(R.id.next)
        val email = findViewById<EditText>(R.id.email)
        val password = findViewById<EditText>(R.id.password)
        val error = findViewById<TextView>(R.id.error)
        val progress = findViewById<ProgressBar>(R.id.progress)

        next.setOnClickListener {
            error.text = ""
            if(!isValidEmail(email.text.toString())) {
                error.text = "Invalid email address"
                return@setOnClickListener
            }
            if(password.text.toString().length < 6) {
                error.text = "Password is too short!"
                return@setOnClickListener
            }

            progress.visibility = View.VISIBLE
            val loginBody = LoginBody(
                email.text.toString(),
                password.text.toString(),
            )

            val apiInterface = ApiInterface.create().loginUser(loginBody)

            apiInterface.enqueue( object : Callback<Message> {

                override fun onResponse(call: Call<Message>?, response: Response<Message>?) {
                    progress.visibility = View.GONE
                    System.out.println(response?.body())
                    if(response?.body()?.success !== null){
                        error.text = response?.body()?.success
                        editor.putString("publictoken",response?.body()?.token!!)
                        System.out.println("the stored token is " + response?.body()?.token)
                        editor.commit()
                        startActivity(Intent(this@PublicLoginPage,Incidences::class.java))
                        finish()
                    }
                    else {
                        editor.putString("token","")
                        editor.commit()
                        error.text = response?.body()?.error
                    }
                }
                override fun onFailure(call: Call<Message>?, t: Throwable?) {
                    progress.visibility = View.GONE
                    System.out.println("The error is $t")
                    error.text = "Connection to server failed"
                    editor.putString("token","")
                    editor.commit()
                }
            })
        }
    }

    private fun registerUser(d:Dialog) {
        val regsubmit = d.findViewById<Button>(R.id.regSubmit)
        val regname = d.findViewById<EditText>(R.id.name)
        val regemail = d.findViewById<EditText>(R.id.email)
        val regphone = d.findViewById<EditText>(R.id.phone)
        val regpassword = d.findViewById<EditText>(R.id.password)
        val error = d.findViewById<TextView>(R.id.error)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val hide = d.findViewById<ConstraintLayout>(R.id.parent)

        hide.setOnClickListener {
            d.hide()
        }

        regsubmit.setOnClickListener {
            error.text = ""
            if(!isValidEmail(regemail.text.toString())) {
                error.text = "Invalid email address"
                error.text = ""
                return@setOnClickListener
            }

            if (regname.text.toString() == ""){
                error.text = "Please fill all entries"
            }

            if (regphone.text.toString() == ""){
                error.text = "Please fill all entries"
            }

            if(regpassword.text.toString().length < 6) {
                error.text = "Password is too short!"
                return@setOnClickListener
            }

            progress.visibility = View.VISIBLE
            val regBody = RegBody(
                regname.text.toString(),
                regemail.text.toString(),
                regphone.text.toString(),
                regpassword.text.toString()
            )

            val apiInterface = ApiInterface.create().registerUser(regBody)

            apiInterface.enqueue( object : Callback<Message> {

                override fun onResponse(call: Call<Message>?, response: Response<Message>?) {
                    progress.visibility = View.GONE
                    System.out.println(response?.body())
                    if(response?.body()?.success !== null){
                        error.text = response?.body()?.success
                        lifecycleScope.launch {
                            delay(3000)
                            regdialog.hide()
                        }

                        startActivity(Intent(this@PublicLoginPage,LoginPage::class.java))
                        finish()
                    }
                    else {
                        editor.putString("token","")
                        editor.commit()
                        error.text = response?.body()?.error
                    }
                }
                override fun onFailure(call: Call<Message>?, t: Throwable?) {
                    System.out.println(t)
                    error.text = "Connection to server failed"
                    editor.putString("token","")
                    editor.commit()
                }
            })
        }
    }

    private fun getNewPassword(d:Dialog) {
        val dialogSubmit = d.findViewById<Button>(R.id.dialogSubmit)
        val dialogEmail = d.findViewById<EditText>(R.id.dialogEmail)
        val error = d.findViewById<TextView>(R.id.error)

        val hide = d.findViewById<ConstraintLayout>(R.id.parent)

        hide.setOnClickListener {
            d.hide()
        }

        dialogSubmit.setOnClickListener {
            error.text = ""
            if(!isValidEmail(dialogEmail.text.toString())) {
                error.text = "Invalid email address"
                error.text = ""
                return@setOnClickListener
            }

            val passwordRecover = RecoverPasswordBody(
                dialogEmail.text.toString()
            )

            val apiInterface = ApiInterface.create().recoverPassword(passwordRecover)

            apiInterface.enqueue( object : Callback<Message> {

                override fun onResponse(call: Call<Message>?, response: Response<Message>?) {

                    System.out.println(response?.body())
                    if(response?.body()?.success !== null){
                        error.text = response?.body()?.success
                        lifecycleScope.launch {
                            delay(3000)
                            fpdialog.hide()
                        }
                    }
                    else {
                        editor.putString("token","")
                        editor.commit()
                        error.text = response?.body()?.error
                    }
                }
                override fun onFailure(call: Call<Message>?, t: Throwable?) {
                    System.out.println(t)
                    error.text = "Connection to server failed"
                    editor.putString("token","")
                    editor.commit()
                }
            })
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val pattern: Pattern = Patterns.EMAIL_ADDRESS
        return pattern.matcher(email).matches()
    }

}