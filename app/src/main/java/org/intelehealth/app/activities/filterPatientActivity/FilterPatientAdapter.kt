package org.intelehealth.app.activities.filterPatientActivity

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.intelehealth.app.R
import org.intelehealth.app.models.dto.PatientDTO
import org.intelehealth.app.utilities.SessionManager
import org.intelehealth.app.utilities.StringUtils

class FilterPatientAdapter(private var patientList: List<PatientDTO>, private val listener: AdapterClickListener): RecyclerView.Adapter<FilterPatientAdapter.FilterPatientViewHolder>() {
  private var lastSelectedPosition = -1

  interface AdapterClickListener {
    fun onItemClick(selectedItem: Any)
  }

  inner class FilterPatientViewHolder(val itemView: View): RecyclerView.ViewHolder(itemView) {
    private val patientSelectButton = itemView.findViewById<RadioButton>(R.id.filter_patient_selected)
    private val patientPhoneTv = itemView.findViewById<TextView>(R.id.filter_txt_phone)
    private val patientGender = itemView.findViewById<TextView>(R.id.filter_txt_gender)
    private val patientNameTv = itemView.findViewById<TextView>(R.id.filter_txt_name)
    private val patientDobTv = itemView.findViewById<TextView>(R.id.filter_txt_dob)
    private val sessionManager = SessionManager(itemView.context)

    fun onBindView(patient: PatientDTO) {
      StringUtils.setGenderAgeLocal(itemView.context, patientGender, patient.dateofbirth, patient.gender, sessionManager)
      patientNameTv.text = "${patient.firstname} ${patient.lastname}"
      patientDobTv.text = StringUtils.en_hi_dob_three(patient.dateofbirth)
      patientPhoneTv.text = patient.phonenumber
      patientSelectButton.isChecked = absoluteAdapterPosition == lastSelectedPosition

      itemView.setOnClickListener {
        val selectedPosition = absoluteAdapterPosition

        if(selectedPosition != lastSelectedPosition) {
          val prevSelectedPosition = lastSelectedPosition
          lastSelectedPosition = selectedPosition

          notifyItemChanged(prevSelectedPosition)
          notifyItemChanged(lastSelectedPosition)

          listener.onItemClick(patient)
        }
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterPatientViewHolder {
    return LayoutInflater.from(parent.context).inflate(R.layout.filter_patient_item_layout, parent, false).run {
      FilterPatientViewHolder(this)
    }
  }

  override fun getItemCount() = patientList.size

  override fun onBindViewHolder(holder: FilterPatientViewHolder, position: Int) {
    holder.onBindView(patientList[position])
  }

  fun updatePatientList(newPatients: List<PatientDTO>) {
    lastSelectedPosition = -1
    patientList = newPatients
    notifyDataSetChanged()
  }
}