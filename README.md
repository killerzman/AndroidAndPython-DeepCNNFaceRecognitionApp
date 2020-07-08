# Deep CNN Face Recognition App
This project trains deep learning models on databases with human faces to be recognized. These models are afterwards converted in a lite format and implemented on Android to measure accuracy and time
<br />
<br />

# Project Structure
## Android
- Contains the Android application used for facial recognition
- Created with Android Studio
- Ability to choose deep learning model and face database to test from a list
- Select photo from gallery or take one with the camera
- Measure accuracy and time

## Python
- Contains .ipynb script used on Google Colab to train models
- Contains archive with the face databases used
<br />

# Deep learning models used
- [MobileNet](https://arxiv.org/abs/1704.04861)
- [EffNet](https://arxiv.org/abs/1801.06434)
- [L-CNN](https://github.com/radu-dogaru/LightWeight_Binary_CNN_and_ELM_Keras)

# Face databases used
- [Essex](https://cswww.essex.ac.uk/mv/allfaces/)
- [Jaffe](https://zenodo.org/record/3451524)
- [ORL](http://cam-orl.co.uk/facedatabase.html)
