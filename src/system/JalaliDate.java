package system;
import java.io.Serializable;
public class JalaliDate implements Serializable {
    private int year;
    private int month;
    private int day;

    public JalaliDate(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }


    public boolean isLeapYear() {
        int remainder = year % 33;
        // در تقویم شمسی اگر باقیمانده سال بر 33 یکی از اعداد زیر باشد، سال کبیسه است
        if (remainder == 1 || remainder == 5 || remainder == 9 || remainder == 13 ||
                remainder == 17 || remainder == 22 || remainder == 26 || remainder == 30) {
            return true;
        }
        return false;
    }

    // متد برای رفتن به روز بعد (تیک زدن تقویم)
    public void advanceOneDay() {
        day++;
        int maxDays = 31;

        if (month >= 7 && month <= 11) {
            maxDays = 30;
        } else if (month == 12) {
            if (isLeapYear()) {
                maxDays = 30;
            } else {
                maxDays = 29;
            }
        }

        if (day > maxDays) {
            day = 1;
            month++;
            if (month > 12) {
                month = 1;
                year++;
            }
        }
    }

    public String getMonthName() {
        String[] months = {"Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
                "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"};
        return months[month - 1];
    }

    public int getMonth() {
        return month;
    }

    public String getFullDate() {
        return year + "/" + month + "/" + day + " (" + getMonthName() + ")";
    }
    public int getYear() {
        return year;
    }
}