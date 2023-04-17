# hubitat-wallbox
Hubitat integration with wallbox ev chargers

**Introduction**

This driver is for integrating Hubitat with your Wallbox EV Charger.


**Pre-Requisites**

1.	A Wallbox EV Charger connected to the intenet
2.	Your credentials for the my.wallbox portal
3.	Your charger ID. This is the charger's serial number that you can see in Wallbox app, Charger Info screen

**Supported Features**
The initial version of the Hubitat drivers support the following capabilities:
1.	Retrieve certain attributes of the charger (i selected the ones I thought were useful, there are more let me know if you need any to add them)
2.	Pause/Resume charging
3.	Set maximum charging current (in amperes, varies with the model/region - in Europe 6A to 32A)
4.	Restart the charger


**Not supported features**

I have built the driver for a single charger. It would be possible to auto-detect multiple chargers in an account, as well as the groups hierarchy used by wallbox, but for now I created a simple one-device driver. If there is the need for more complex implementation I may consider in the future.

I have only tested with a Pulsar Plus charger, European 22kw version.


**Installation**

1.	Copy the code for the driver into Hubitat
2.	Create a new Virtual Device of type Wallbox Charger
3.	Enter the correct parameters for the configuration
    a.	Username used for the wallbox portal
    b.	Password used for the wallbox portal
    c.	Id of the charger (as described in pre-requisites)
    d.	Token validity: driver refreshes access token if more than x days have passed. As far as I know, validity currently stands at 15 days
    e.	Log Level:
      i.	0: Error only
      ii.	1: Also Warnings
      iii.	2: Also Information
      iv.	3: Also Debug
      v.	4: Also Trace
    f.	Press Save Preferences
4.	Press Refresh to retrieve charger data


** Usage **

I believe commands are self explanatory

** Credits **
Built inspired by the excellent work of https://github.com/flhoest/Wallbox
