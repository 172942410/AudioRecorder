package com.perry.audiorecorder.bean;

import android.text.TextUtils;

import java.util.List;

public class ReceiveMsgBean {
    public static final int NO_ID = -1;
    public int id;
    public String text;
    public List<Question> question;
    public Fault fault;

    /**
     *
     * 查询到故障编码：fault_code，故障为：fault_name，所属：belong_system系统，限速要求：speed，处理方法：solve_method
     *
     * @return
     */
    @Override
    public String toString() {
        return "ReceiveMsgBean{" +
                "id=" + id +
                ", text='" + text + '\'' +
                '}';
    }

    /**
     * 故障编码(code), 车组号(car_num),随车机械师(worker),地点(address)
     *
     * "belong_system": "所属系统",
     *         "fault_code": "故障编码",
     *         "fault_name": "故障名称",
     *         "check_tips": "检查提示",
     *         "driver_stop_tips": "停车指引",
     *         "driver_run_tips": "运行指引",
     *         "fault_level": "故障等级",
     *         "fault_type": "故障类型",
     *         "fault_situation": "故障情形",
     *         "solve_method": "处理方法",
     *         "speed": "限速要求",
     *         "train_response": "车组响应"
     *
     * @return
     */
    public String showMsg() {
        StringBuilder msgStringBuilder = new StringBuilder();
        if(question != null && question.size() > 0){
            Question q = question.get(0);
            if(!TextUtils.isEmpty(q.code)){
                msgStringBuilder.append("故障编码(").append(q.code).append("),");
            }
            if(!TextUtils.isEmpty(q.car_num)){
                msgStringBuilder.append("车组号(").append(q.car_num).append("),");
            }
            if(!TextUtils.isEmpty(q.worker)){
                msgStringBuilder.append("随车机械师(").append(q.worker).append("),");
            }
            if(!TextUtils.isEmpty(q.address)){
                msgStringBuilder.append("地点(").append(q.address).append("),");
            }
        }
        if(fault != null){
            if(msgStringBuilder.length() > 0) {
                msgStringBuilder.append("\n");
            }
            if(!TextUtils.isEmpty(fault.error)){
                msgStringBuilder.append(fault.error);
            }else {
                if (!TextUtils.isEmpty(fault.belong_system)) {
                    msgStringBuilder.append("所属系统：").append(fault.belong_system).append("，");
                }
                if (!TextUtils.isEmpty(fault.fault_code)) {
                    msgStringBuilder.append(" 故障编码：").append(fault.fault_code).append("，");
                }
                if (!TextUtils.isEmpty(fault.fault_name)) {
                    msgStringBuilder.append(" 故障名称：").append(fault.fault_name).append("，");
                }
                if (!TextUtils.isEmpty(fault.check_tips)) {
                    msgStringBuilder.append(" 检查提示：").append(fault.check_tips).append("，");
                }
                if (!TextUtils.isEmpty(fault.driver_stop_tips)) {
                    msgStringBuilder.append(" 停车指引：").append(fault.driver_stop_tips).append("，");
                }
                if (!TextUtils.isEmpty(fault.driver_run_tips)) {
                    msgStringBuilder.append(" 运行指引：").append(fault.driver_run_tips).append("，");
                }
                if (!TextUtils.isEmpty(fault.fault_level)) {
                    msgStringBuilder.append(" 故障等级：").append(fault.fault_level).append("，");
                }
                if (!TextUtils.isEmpty(fault.fault_type)) {
                    msgStringBuilder.append(" 故障类型：").append(fault.fault_type).append("，");
                }
                if (!TextUtils.isEmpty(fault.fault_situation)) {
                    msgStringBuilder.append(" 故障情形：").append(fault.fault_situation).append("，");
                }
                if (!TextUtils.isEmpty(fault.solve_method)) {
                    msgStringBuilder.append(" 处理方法：").append(fault.solve_method).append("，");
                }
                if (!TextUtils.isEmpty(fault.speed)) {
                    msgStringBuilder.append(" 限速要求：").append(fault.speed).append("，");
                }
                if (!TextUtils.isEmpty(fault.train_response)) {
                    msgStringBuilder.append(" 车组响应：").append(fault.train_response).append("，");
                }
            }
        }
        return  msgStringBuilder.toString();
    }

    /**
     * 查询到故障编码：fault_code，故障为：fault_name，所属：belong_system系统，限速要求：speed，处理方法：solve_method
     * @return
     */
    public String speakMsg() {
        StringBuilder faultSb = new StringBuilder();
        if(fault != null){
            if(!TextUtils.isEmpty(fault.error)){
                faultSb.append(fault.error);
            }else {
                if (!TextUtils.isEmpty(fault.fault_code)) {
                    faultSb.append("查询到故障编码：").append(fault.fault_code);
                }
                if (!TextUtils.isEmpty(fault.fault_name)) {
                    faultSb.append("，故障为：").append(fault.fault_name);
                }
                if (!TextUtils.isEmpty(fault.belong_system)) {
                    faultSb.append("，所属：").append(fault.belong_system).append("系统");
                }
                if (!TextUtils.isEmpty(fault.speed)) {
                    faultSb.append("，限速要求：").append(fault.speed);
                }
                if (!TextUtils.isEmpty(fault.solve_method)) {
                    faultSb.append("，处理方法：").append(fault.solve_method);
                }
            }
        }
        return faultSb.toString();
    }

    public class Question{
        public String code;
        public String car_model;
        public String car_num;
        public String car_searial;
        public String worker;
        public String address;

        /**
         * 显示：
         * 故障编码(code), 车组号(car_num),随车机械师(worker),地点(address)
         * 和
         *
         * @return
         */
        @Override
        public String toString() {
            return "Question{" +
                    "code='" + code + '\'' +
                    ", car_model='" + car_model + '\'' +
                    ", car_num='" + car_num + '\'' +
                    ", car_searial='" + car_searial + '\'' +
                    ", worker='" + worker + '\'' +
                    ", address='" + address + '\'' +
                    '}';
        }
    }

    /**
     * "belong_system": "所属系统",
     *         "fault_code": "故障编码",
     *         "fault_name": "故障名称",
     *         "check_tips": "检查提示",
     *         "driver_stop_tips": "停车指引",
     *         "driver_run_tips": "运行指引",
     *         "fault_level": "故障等级",
     *         "fault_type": "故障类型",
     *         "fault_situation": "故障情形",
     *         "solve_method": "处理方法",
     *         "speed": "限速要求",
     *         "train_response": "车组响应"
     *
     *         {"question":[{"code":"10000"}],"fault":{"error":"未查询到故障代码10000相关信息"},"text":"故障边码一万有吗?"}
     */
    public class Fault{
        public String belong_system;
        public String fault_code;
        public String fault_name;
        public String check_tips;
        public String driver_stop_tips;
        public String driver_run_tips;
        public String fault_level;
        public String fault_type;
        public String fault_situation;
        public String solve_method;
        public String speed;
        public String train_response;
        public String WTDS;
        public String error;

        @Override
        public String toString() {
            return "Fault{" +
                    "belong_system='" + belong_system + '\'' +
                    ", fault_code='" + fault_code + '\'' +
                    ", fault_name='" + fault_name + '\'' +
                    ", check_tips='" + check_tips + '\'' +
                    ", driver_stop_tips='" + driver_stop_tips + '\'' +
                    ", driver_run_tips='" + driver_run_tips + '\'' +
                    ", fault_level='" + fault_level + '\'' +
                    ", fault_type='" + fault_type + '\'' +
                    ", fault_situation='" + fault_situation + '\'' +
                    ", solve_method='" + solve_method + '\'' +
                    ", speed='" + speed + '\'' +
                    ", train_response='" + train_response + '\'' +
                    ", WTDS='" + WTDS + '\'' +
                    ", error='" + error + '\'' +
                    '}';
        }
    }
}
