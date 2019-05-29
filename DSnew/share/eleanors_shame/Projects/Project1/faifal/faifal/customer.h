#ifndef CUSTOMER_H
#define CUSTOMER_H

#include <iostream>
#include <fstream>
#include <iomanip>
#include <functional>
#include <algorithm>
#include <string>
#include <cstdlib>
#include <sstream>
using namespace std;

//Customer Class
class Customer {
private:
    string name;  //name of customer
    string ssn; // social security number
    string address;  // address
    int birthDate;      //birth date
    double savings;  // savings account balance
    double checking;  // checking account balance
    double balance;    // Consoldated Balance

public:
    Customer(); //constructor
    Customer(string, string, string, int ,int, int, int);
    void setName(string);
    void setSSN(string);
    void setAddress(string);
    void setBirthDate(int);
    void setSavings(double);
    void setChecking(double);
        void setBalance(double);
        
        void operator +=( double );
        void operator -=( double );
        void operator ++ ();

    string getName();
    string getSSN();
    string getAddress();
    int getBirthDate();
    double getSavings();
    double getChecking();
    double getBalance();

        void displayCustomer();
        string string_displayCustomer();


};


//-----------------------------------------------------------------------------
//class definition

#endif
