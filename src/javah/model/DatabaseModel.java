package javah.model;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javah.container.Resident;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import javah.util.DatabaseContract;
import javah.util.DatabaseContract.*;


public class DatabaseModel {
    private MysqlDataSource dataSource;

    /**
     * Determines what type of data are to be stored.
     */
    private byte BINARY_PHOTO = 0,
                 BINARY_SIGNATURE = 1;

    /**
     * The path pointing to the data directories of the application.
     */
    private String mPhotoDirectoryPath, mSignatureDirectoryPath;

    /**
     * Bug: Apparently, using parametarized query with a data source connection to allow connection pooling converts the
     * expected results to their corresponding column names. (e.g. 131-005 == resident_id).
     */
    public DatabaseModel() {
        // Initialize the data source.
        dataSource = new MysqlDataSource();
        dataSource.setURL("jdbc:mysql://localhost/BarangayDB");
        dataSource.setUser("root");
        dataSource.setPassword("horizon");

        // Make path towards the root folder 'Barangay131' at C:\Users\Public and its sub-folders - 'Photos' and 'Signature'.
        String dataDirectoryPath = System.getenv("PUBLIC") + "/Barangay131";

        mPhotoDirectoryPath = dataDirectoryPath + "/Photos";
        mSignatureDirectoryPath = dataDirectoryPath + "/Signatures";

        // Create the directories if not yet created.
        File photoDirectory = new File(mPhotoDirectoryPath);
        if(!photoDirectory.exists())
            photoDirectory.mkdir();

        File signatureDirectory = new File(mSignatureDirectoryPath);
        if(!signatureDirectory.exists())
            signatureDirectory.mkdir();

    }

    /**
     * Return the non-archived residents IDs and Names.
     * @return an array of lists, where List[0] contains the resident IDs, while List[1] contains the concatenated
     * resident names.
     */
    public List[] getResidentsIdAndName() {

        List[] returnList = new List[2];
        List<String> residentsIdList = new ArrayList<>();
        List<String> residentNameList = new ArrayList<>();

        try {
            Connection dbConnection = dataSource.getConnection();

            // Use String.format as a workaround to the bug when using parameterized query.
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    String.format("SELECT %s, %s, %s, %s FROM %s WHERE %s = 0 ORDER BY %s",
                            DatabaseContract.COLUMN_ID,
                            ResidentEntry.COLUMN_FIRST_NAME,
                            ResidentEntry.COLUMN_MIDDLE_NAME,
                            ResidentEntry.COLUMN_LAST_NAME,
                            ResidentEntry.TABLE_NAME,
                            ResidentEntry.COLUMN_IS_ARCHIVED,
                            ResidentEntry.COLUMN_LAST_NAME)
                    );

            ResultSet resultSet = preparedStatement.executeQuery();

            while(resultSet.next()) {
                residentsIdList.add(resultSet.getString(DatabaseContract.COLUMN_ID));

                residentNameList.add(String.format("%s, %s %s.",
                        resultSet.getString(ResidentEntry.COLUMN_LAST_NAME),
                        resultSet.getString(ResidentEntry.COLUMN_FIRST_NAME),
                        resultSet.getString(ResidentEntry.COLUMN_MIDDLE_NAME).toUpperCase().charAt(0)));
            }

            returnList[0] = residentsIdList;
            returnList[1] = residentNameList;

            dbConnection.close();
            preparedStatement.close();
            resultSet.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return returnList;
    }

    public Resident getResident(String residentId) {

        try {
            Connection dbConnection = dataSource.getConnection();

            // Use String.format as a workaround to the bug when using parameterized query.
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    String.format("SELECT %s, %s, %s, %s, %s, %s, %s, %s, %s FROM %s WHERE %s = ?",
                            ResidentEntry.COLUMN_FIRST_NAME,
                            ResidentEntry.COLUMN_MIDDLE_NAME,
                            ResidentEntry.COLUMN_LAST_NAME,
                            ResidentEntry.COLUMN_BIRTH_DATE,
                            ResidentEntry.COLUMN_PHOTO,
                            ResidentEntry.COLUMN_YEAR_OF_RESIDENCY,
                            ResidentEntry.COLUMN_MONTH_OF_RESIDENCY,
                            ResidentEntry.COLUMN_ADDRESS_1,
                            ResidentEntry.COLUMN_ADDRESS_2,
                            ResidentEntry.TABLE_NAME,
                            DatabaseContract.COLUMN_ID
                    )
            );

            preparedStatement.setString(1, residentId);

            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                Resident resident = new Resident();

                resident.setId(residentId);
                resident.setFirstName(resultSet.getString(ResidentEntry.COLUMN_FIRST_NAME));
                resident.setMiddleName(resultSet.getString(ResidentEntry.COLUMN_MIDDLE_NAME));
                resident.setLastName(resultSet.getString(ResidentEntry.COLUMN_LAST_NAME));
                resident.setBirthDate(resultSet.getDate(ResidentEntry.COLUMN_BIRTH_DATE));
                resident.setPhotoPath(resultSet.getString(ResidentEntry.COLUMN_PHOTO));
                resident.setYearOfResidency(resultSet.getShort(ResidentEntry.COLUMN_YEAR_OF_RESIDENCY));
                resident.setMonthOfResidency(resultSet.getShort(ResidentEntry.COLUMN_MONTH_OF_RESIDENCY));
                resident.setAddress1(resultSet.getString(ResidentEntry.COLUMN_ADDRESS_1));
                resident.setAddress2(resultSet.getString(ResidentEntry.COLUMN_ADDRESS_2));

                return resident;
            }

            dbConnection.close();
            preparedStatement.close();
            resultSet.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public void archiveResident(String residentId) {
        try {
            Connection dbConnection = dataSource.getConnection();

            // Use String.format as a workaround to the bug when using parameterized query.
            PreparedStatement preparedStatement = dbConnection.prepareStatement(
                    String.format("UPDATE %s SET %s = 1 WHERE %s = ?",
                            ResidentEntry.TABLE_NAME,
                            ResidentEntry.COLUMN_IS_ARCHIVED,
                            DatabaseContract.COLUMN_ID
                    )
            );

            preparedStatement.setString(1, residentId);
            preparedStatement.executeUpdate();

            dbConnection.close();
            preparedStatement.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String createResident(Resident resident) {

        if(resident.getPhotoPath() != null) {
            resident.setPhotoPath(copyFile(resident.getPhotoPath(), BINARY_PHOTO));
        }

        try {
            Connection dbConnection = dataSource.getConnection();

            String residentID = generateID(ResidentEntry.TABLE_NAME);

            PreparedStatement statement = dbConnection.prepareStatement(
                    String.format("INSERT INTO %s(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) " +
                                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, default)",
                            ResidentEntry.TABLE_NAME,
                            DatabaseContract.COLUMN_ID,
                            ResidentEntry.COLUMN_FIRST_NAME,
                            ResidentEntry.COLUMN_MIDDLE_NAME,
                            ResidentEntry.COLUMN_LAST_NAME,
                            ResidentEntry.COLUMN_BIRTH_DATE,
                            ResidentEntry.COLUMN_PHOTO,
                            ResidentEntry.COLUMN_YEAR_OF_RESIDENCY,
                            ResidentEntry.COLUMN_MONTH_OF_RESIDENCY,
                            ResidentEntry.COLUMN_ADDRESS_1,
                            ResidentEntry.COLUMN_ADDRESS_2,
                            ResidentEntry.COLUMN_IS_ARCHIVED));

            statement.setString(1, residentID);
            statement.setString(2, resident.getFirstName());
            statement.setString(3, resident.getMiddleName());
            statement.setString(4, resident.getLastName());
            statement.setDate(5, resident.getBirthDate());
            statement.setString(6, resident.getPhotoPath());
            statement.setInt(7, resident.getYearOfResidency());
            statement.setInt(8, resident.getMonthOfResidency());
            statement.setString(9, resident.getAddress1());
            statement.setString(10, resident.getAddress2());

            statement.execute();

            statement.close();
            dbConnection.close();

            return residentID;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Generate a unique id for the given table.
     * @param tableName
     * @return the uniquely generated id.
     */
    private String generateID(String tableName) {
        try {
            Connection dbConnection = dataSource.getConnection();

            // Generate a unique resident id.
            String residentId = "";
            PreparedStatement statement = dbConnection.prepareStatement(
                    String.format("SELECT %s FROM %s WHERE %s LIKE '%s%%' ORDER BY %s DESC LIMIT 1",
                            DatabaseContract.COLUMN_ID,
                            tableName,
                            DatabaseContract.COLUMN_ID,
                            Calendar.getInstance().get(Calendar.YEAR) - 2000,
                            DatabaseContract.COLUMN_ID));

            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                String code = resultSet.getString(DatabaseContract.COLUMN_ID);
                int year = Integer.parseInt(code.substring(0, 2));
                int codeNo = Integer.parseInt(code.substring(3, 6)) + 1;

                residentId = year + "-" + (codeNo < 10 ? "00" + codeNo : codeNo < 100 ? "0" + codeNo : codeNo);
            } else {
                int year = Calendar.getInstance().get(Calendar.YEAR) - 2000;
                residentId = year + "-001";
            }

            statement.close();
            resultSet.close();
            dbConnection.close();

            return residentId;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Make a copy of a binary file to be stored at the data folder of the application.
     * @param sourceFilePath is a string path of the source file.
     * @param binaryGroup determines which directory should the target file be placed.
     * @return the string path of the target file.
     */
    private String copyFile(String sourceFilePath, byte binaryGroup) {
        File sourceFile = new File(sourceFilePath);

        // Get the file extension.
        String fileExtension = sourceFile.getName().substring(sourceFile.getName().lastIndexOf("."));

        // Initialize the target file.
        String targetFilePath = sourceFilePath;
        File targetFile = sourceFile;

        // Determine which directory to store the target file.
        if(binaryGroup == BINARY_PHOTO) {
            // Generate a unique name for the target file.
            targetFilePath = mPhotoDirectoryPath + "/" + UUID.randomUUID() + fileExtension;
            targetFile = new File(targetFilePath);
        }

        // Let the target file copy the source file and save it.
        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return targetFilePath;
    }
}