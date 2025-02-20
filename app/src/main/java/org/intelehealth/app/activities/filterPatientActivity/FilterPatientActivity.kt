package org.intelehealth.app.activities.filterPatientActivity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.intelehealth.app.BuildConfig
import org.intelehealth.app.R
import org.intelehealth.app.activities.onboarding.PrivacyPolicyActivity_New
import org.intelehealth.app.activities.patientDetailActivity.PatientDetailActivity2
import org.intelehealth.app.app.AppConstants
import org.intelehealth.app.database.dao.PatientsDAO
import org.intelehealth.app.models.dto.PatientDTO
import org.intelehealth.app.models.dto.ResponseDTO
import org.intelehealth.app.shared.BaseActivity
import org.intelehealth.app.utilities.DialogUtils
import org.intelehealth.app.utilities.ToastUtil

class FilterPatientActivity: BaseActivity(), FilterPatientAdapter.AdapterClickListener {
  private lateinit var filterSuccessLayout: LinearLayout
  private lateinit var filterFailedLayout: LinearLayout
  private lateinit var filterRecyclerView: RecyclerView
  private lateinit var goWithSelectedButton: Button
  private lateinit var loadingDialog: AlertDialog
  private lateinit var genderSpinner: Spinner
  private lateinit var firstNameTv: TextView
  private lateinit var lastNameTv: TextView
  private lateinit var phoneTv: TextView
  private lateinit var monthTv: TextView
  private lateinit var yearTv: TextView
  private lateinit var dayTv: TextView

  private val patientsDAO = PatientsDAO()
  private var selectedPatient: PatientDTO? = null
  private var patientList = mutableListOf<PatientDTO>()
  private val subscriptions: CompositeDisposable = CompositeDisposable()
  private val patientAdapter = FilterPatientAdapter(patientList, this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_filter_patient)

    filterRecyclerView = findViewById(R.id.filter_patient_container)
    filterRecyclerView.layoutManager = LinearLayoutManager(this@FilterPatientActivity)
    filterRecyclerView.adapter = patientAdapter

    loadingDialog = DialogUtils().showCommonLoadingDialog(
      this@FilterPatientActivity,
      getString(R.string.loading),
      getString(R.string.please_wait),
      ).apply {
        dismiss()
    }

    filterSuccessLayout = findViewById(R.id.filter_patient_success_ll)
    filterFailedLayout = findViewById(R.id.filter_patient_failed_ll)
    goWithSelectedButton = findViewById(R.id.btn_with_selected_patient)

    genderSpinner = findViewById(R.id.filter_txt_gender)
    firstNameTv = findViewById(R.id.filter_txt_first_name)
    lastNameTv = findViewById(R.id.filter_txt_last_name)
    phoneTv = findViewById(R.id.filter_txt_phone)
    monthTv = findViewById(R.id.filter_txt_dob_month)
    yearTv = findViewById(R.id.filter_txt_dob_year)
    dayTv = findViewById(R.id.filter_txt_dob_day)

    // changing status bar color
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    window.statusBarColor = Color.WHITE

    findViewById<ImageView>(R.id.iv_back_arrow)?.setOnClickListener {
      finish()
    }

    findViewById<Button>(R.id.btn_filter_patient)?.setOnClickListener {
      if(validateUserInput()) {
        doFilter(
          firstName = firstNameTv.text.toString(),
          lastName = lastNameTv.text.toString(),
          gender = if(genderSpinner.selectedItemPosition == 1) "M" else "F",
          phone = phoneTv.text.toString(),
          dob = if(dayTv.text.isNotEmpty()) "${yearTv.text}-${String.format("%2s", monthTv.text)}-${String.format("%2s", dayTv.text)}" else ""
        )
      }
    }

    findViewById<Button>(R.id.btn_create_new_patient)?.setOnClickListener {
      goToCreateNewPatient()
    }

    findViewById<LinearLayout>(R.id.add_new_patient_ll)?.setOnClickListener {
      goToCreateNewPatient()
    }

    goWithSelectedButton.setOnClickListener {
      goToPatientDetails()
    }
  }

  override fun onItemClick(selectedItem: Any) {
    selectedPatient = selectedItem as PatientDTO
    goWithSelectedButton.isEnabled = true
  }

  override fun onDestroy() {
    super.onDestroy()
    subscriptions.clear()
  }

  private fun validateUserInput(): Boolean {
    var isValid = true

    if(firstNameTv.text.isNullOrEmpty()) {
      isValid = false
      firstNameTv.error = "required"
    }

    if(genderSpinner.selectedItemPosition == 0) {
      isValid = false
      ToastUtil.showLongToast(this@FilterPatientActivity, "Select Gender")
    }

    val day = dayTv.text.toString()
    val month = monthTv.text.toString()
    val year = yearTv.text.toString()

    if(!((day.isNotEmpty() && month.isNotEmpty() && year.isNotEmpty()) || (day.isEmpty() && month.isEmpty() && year.isEmpty()))) {
      isValid = false
      ToastUtil.showLongToast(this@FilterPatientActivity, "Type valid DoB")
    }

    if(day.isNotEmpty() && (day.length < 2 || day.toInt() > 31)) {
      isValid = false
      dayTv.error = "type valid day"
    }

    if(month.isNotEmpty() && (month.length < 2 || month.toInt() > 12)) {
      isValid = false
      monthTv.error = "type valid month"
    }

    if(year.isNotEmpty() && year.length < 4) {
      isValid = false
      monthTv.error = "type valid year"
    }



    return isValid
  }

  private fun doFilter(firstName: String, lastName: String, gender: String, phone: String, dob: String) {
    getSystemService(INPUT_METHOD_SERVICE)?.let { imm ->
      currentFocus?.let {
        (imm as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0)
      }
    }

    subscriptions.add(
      Observable.fromCallable { PatientsDAO.getFilteredPatients(firstName, lastName, gender, phone, dob) }
        .concatMap {
          if (it.isEmpty()) {
            findRemotePatientObservable(firstName, lastName, gender, phone, dob)
              .concatMap { response ->
                response.data?.patientDTO?.let { remotePatients ->
                  patientsDAO.insertPatients(remotePatients)
                }

                Observable.just(PatientsDAO.getFilteredPatients(firstName, lastName, gender, phone, dob))
              }
          } else {
            Observable.just(it)
          }
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSubscribe { loadingDialog.show() }
        .doOnTerminate { loadingDialog.dismiss() }
        .subscribe(
          { updatePatientsAdapter(it) },
          { ToastUtil.showLongToast(this@FilterPatientActivity, "Error finding patients") }
        )
    )
  }

  private fun findRemotePatientObservable(firstName: String, lastName: String, gender: String, phone: String, dob: String): Observable<ResponseDTO> {
    val urlBuilder = StringBuilder()
      .append(BuildConfig.SERVER_URL)
      .append("/EMR-Middleware/webapi/pull/pulldata/search?firstname=")
      .append(firstName).append("&gender=").append(gender)

    lastName.ifEmpty { null }?.let { urlBuilder.append("&lastname=").append(lastName) }
    phone.ifEmpty { null }?.let { urlBuilder.append("&telecom=").append(phone) }
    dob.ifEmpty { null }?.let { urlBuilder.append("&dob=").append(dob) }
    val url = urlBuilder.toString()

    return AppConstants.apiInterface.RESPONSE_DTO_CALL_FOR_FILTER(url, "Basic " + sessionManager.encoded)
  }

  private fun updatePatientsAdapter(patients: List<PatientDTO>) {
    if(patients.isNotEmpty()) {
      selectedPatient = null
      goWithSelectedButton.isEnabled = false

      patientAdapter.updatePatientList(patients)
      filterFailedLayout.visibility = View.GONE
      filterSuccessLayout.visibility = View.VISIBLE
    } else {
      filterSuccessLayout.visibility = View.GONE
      filterFailedLayout.visibility = View.VISIBLE
    }
  }

  private fun goToCreateNewPatient() {
    Intent(this@FilterPatientActivity, PrivacyPolicyActivity_New::class.java).apply {
      putExtra("intentType", "navigateFurther")
      putExtra("add_patient", "add_patient")

      startActivity(this)
    }
  }

  private fun goToPatientDetails() {
    selectedPatient?.let { patient ->
      Intent(this@FilterPatientActivity, PatientDetailActivity2::class.java).apply {
        putExtra("patientUuid", patient.uuid)
        putExtra("patientName", patient.firstname + " " + patient.lastname)
        putExtra("tag", "searchPatient")
        putExtra("hasPrescription", "false")
        putExtra("BUNDLE", Bundle().apply { putSerializable("patientDTO", patient) })

        startActivity(this)
      }
    }
  }
}