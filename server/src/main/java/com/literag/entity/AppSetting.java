package com.literag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 120)
    private String settingKey;

    @Column(name = "setting_value", length = 4000)
    private String settingValue;

    public AppSetting() {
    }

    public AppSetting(String key, String value) {
        this.settingKey = key;
        this.settingValue = value;
    }
}
