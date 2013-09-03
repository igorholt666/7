/*
	Radiobeacon - Openbmap wifi and cell logger
    Copyright (C) 2013  wish7

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openbmap.soapclient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import org.openbmap.db.DataHelper;
import org.openbmap.db.DatabaseHelper;
import org.openbmap.db.Schema;
import org.openbmap.db.model.LogFile;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

/**
 * Exports cells to xml format for later upload.
 */
public class CellExporter {

	private static final String TAG = CellExporter.class.getSimpleName();

	/**
	 * Initial size wifi StringBuffer
	 */
	private static final int WIFI_XML_DEFAULT_LENGTH	= 220;

	/**
	 * Initial size position StringBuffer
	 */
	private static final int POS_XML_DEFAULT_LENGTH	= 170;

	/**
	 * Cursor windows size, to prevent running out of mem on to large cursor
	 */
	private static final int CURSOR_SIZE	= 2000;



	/**
	 * XML header.
	 */
	private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>";

	/**
	 * XML template closing logfile
	 */
	private static final String	CLOSE_LOGFILE	= "\n</logfile>";

	/**
	 * XML template closing scan tag
	 */
	private static final String	CLOSE_SCAN_TAG	= "\n</scan>";

	/**
	 * Entries per log file
	 */
	private static final int CELLS_PER_FILE	= 100;


	private Context mContext;

	/**
	 * Session Id to export
	 */
	private int mSession;

	/**
	 * Message in case of an error
	 */
	private String errorMsg = null;

	/**
	 * Datahelper
	 */
	private DataHelper mDataHelper;

	/**
	 * Directory where xmls files are stored
	 */
	private String	mTempPath;

	private int colNetworkType;
	private int colIsCdma;
	private int colIsServing;
	private int colIsNeigbor;
	private int colCellId;
	private int colPsc;
	private int colOperatorName;
	private int colOperator;
	private int colMcc;
	private int colMnc;
	private int colLac;
	private int colStrengthDbm;
	private int colTimestamp;
	private int colPositionId;
	private int colSessionId;

	private int	colReqLat;

	private int	colReqTimestamp;

	private int	colReqLon;

	private int	colReqAlt;

	private int	colReqHead;

	private int	colReqSpeed;

	private int	colReqAcc;

	private int	colBeginPosId;

	private int	colEndPosId;

	private int	colLastLat;

	private int	colLastTimestamp;

	private int	colLastLon;

	private int	colLastAlt;

	private int	colLastHead;

	private int	colLastSpeed;

	private int	colLastAcc;
	
	/**
	 * User name, required for file name generation
	 */
	private String	mUser;

	/**
	 * Timestamp, required for file name generation
	 */
	private Calendar mTimestamp;

	/**
	 * File name contains mcc, this is generated by looking at the first cell
	 */
	private String	fileNameMcc;


	private static final String CELL_SQL_QUERY = " SELECT " + Schema.TBL_CELLS + "." + Schema.COL_ID + ", "
			+ Schema.COL_NETWORKTYPE + ", "
			+ Schema.COL_IS_CDMA + ", "
			+ Schema.COL_IS_SERVING + ", "
			+ Schema.COL_IS_NEIGHBOR + ", "
			+ Schema.COL_CELLID + ", "
			+ Schema.COL_LAC + ", "
			+ Schema.COL_MCC + ", "
			+ Schema.COL_MNC + ", "
			+ Schema.COL_PSC + ", "
			+ Schema.COL_BASEID + ", "
			+ Schema.COL_NETWORKID + ", "
			+ Schema.COL_SYSTEMID + ", "
			+ Schema.COL_OPERATORNAME + ", "
			+ Schema.COL_OPERATOR + ", "
			+ Schema.COL_STRENGTHDBM + ", "
			+ Schema.TBL_CELLS + "." + Schema.COL_TIMESTAMP + ", "
			+ Schema.COL_BEGIN_POSITION_ID + ", "
			+ " \"req\".\"latitude\" AS \"req_latitude\","
			+ " \"req\".\"longitude\" AS \"req_longitude\","
			+ " \"req\".\"altitude\" AS \"req_altitude\","
			+ " \"req\".\"accuracy\" AS \"req_accuracy\","
			+ " \"req\".\"timestamp\" AS \"req_timestamp\","
			+ " \"req\".\"bearing\" AS \"req_bearing\","
			+ " \"req\".\"speed\" AS \"req_speed\", "
			+ " \"last\".\"latitude\" AS \"last_latitude\","
			+ " \"last\".\"longitude\" AS \"last_longitude\","
			+ " \"last\".\"altitude\" AS \"last_altitude\","
			+ " \"last\".\"accuracy\" AS \"last_accuracy\","
			+ " \"last\".\"timestamp\" AS \"last_timestamp\","
			+ " \"last\".\"bearing\" AS \"last_bearing\","
			+ " \"last\".\"speed\" AS \"last_speed\""
			+ " FROM " + Schema.TBL_CELLS 
			+ " JOIN \"" + Schema.TBL_POSITIONS + "\" AS \"req\" ON (" + Schema.COL_BEGIN_POSITION_ID + " = \"req\".\"_id\")"
			+ " JOIN \"" + Schema.TBL_POSITIONS + "\" AS \"last\" ON (" + Schema.COL_END_POSITION_ID + " = \"last\".\"_id\")"
			+ " WHERE " + Schema.TBL_CELLS + "." + Schema.COL_SESSION_ID + " = ?"
			+ " ORDER BY " + Schema.COL_BEGIN_POSITION_ID
			+ " LIMIT " + CURSOR_SIZE
			+ " OFFSET ?";

	/**
	 * Default constructor
	 * @param context	Activities' context
	 * @param session Session id to export
	 * @param tempPath (full) path where temp files are saved. Will be created, if not existing.
	 * @param user Openbmap username, required for filename generation
	 */
	public CellExporter(final Context context, final int session, final String tempPath, final String user) {
		this.mContext = context;
		this.mSession = session;
		this.mTempPath = tempPath;
		this.mUser = user;
		mTimestamp = Calendar.getInstance();

		ensureTempPath(mTempPath);

		mDataHelper = new DataHelper(context);
	}

	/**
	 * Ensures temp file folder is existing and writeable.
	 * If folder not yet exists, it is created
	 */
	private boolean ensureTempPath(final String path) {
		File folder = new File(path);

		boolean folderAccessible = false;
		if (folder.exists() && folder.canWrite()) {
			folderAccessible = true;
		}

		if (!folder.exists()) {
			folderAccessible = folder.mkdirs();
		}
		return folderAccessible;
	}

	/**
	 * Builds cell xml files and saves/uploads them
	 * @return file names of generated files
	 */
	protected final ArrayList<String> export() {
		Log.d(TAG, "Start cell export. Data source: " + CELL_SQL_QUERY);

		final LogFile headerRecord = mDataHelper.loadLogFileBySession(mSession);

		final DatabaseHelper mDbHelper = new DatabaseHelper(mContext);

		ArrayList<String> generatedFiles = new ArrayList<String>();

		// get first CHUNK_SIZE records
		Cursor cursorCells = mDbHelper.getReadableDatabase().rawQuery(CELL_SQL_QUERY,
				new String[]{String.valueOf(mSession), String.valueOf(0)});

		// [start] init columns
		colNetworkType = cursorCells.getColumnIndex(Schema.COL_NETWORKTYPE);
		colIsCdma = cursorCells.getColumnIndex(Schema.COL_IS_CDMA);
		colIsServing = cursorCells.getColumnIndex(Schema.COL_IS_SERVING);
		colIsNeigbor = cursorCells.getColumnIndex(Schema.COL_IS_NEIGHBOR);
		colCellId = cursorCells.getColumnIndex(Schema.COL_CELLID);
		colPsc = cursorCells.getColumnIndex(Schema.COL_PSC);
		colOperatorName = cursorCells.getColumnIndex(Schema.COL_OPERATORNAME);
		colOperator = cursorCells.getColumnIndex(Schema.COL_OPERATOR);
		colMcc = cursorCells.getColumnIndex(Schema.COL_MCC);
		colMnc = cursorCells.getColumnIndex(Schema.COL_MNC);
		colLac = cursorCells.getColumnIndex(Schema.COL_LAC);
		colStrengthDbm = cursorCells.getColumnIndex(Schema.COL_STRENGTHDBM);
		colTimestamp = cursorCells.getColumnIndex(Schema.COL_TIMESTAMP);
		colBeginPosId = cursorCells.getColumnIndex(Schema.COL_BEGIN_POSITION_ID);
		colEndPosId = cursorCells.getColumnIndex(Schema.COL_END_POSITION_ID);
		colSessionId = cursorCells.getColumnIndex(Schema.COL_SESSION_ID);
		colReqLat = cursorCells.getColumnIndex("req_" + Schema.COL_LATITUDE);
		colReqTimestamp = cursorCells.getColumnIndex("req_" + Schema.COL_TIMESTAMP);
		colReqLon = cursorCells.getColumnIndex("req_" + Schema.COL_LONGITUDE);
		colReqAlt = cursorCells.getColumnIndex("req_" + Schema.COL_ALTITUDE);
		colReqHead = cursorCells.getColumnIndex("req_" + Schema.COL_BEARING);
		colReqSpeed = cursorCells.getColumnIndex("req_" + Schema.COL_SPEED);
		colReqAcc = cursorCells.getColumnIndex("req_" + Schema.COL_ACCURACY);
		colLastLat = cursorCells.getColumnIndex("last_" + Schema.COL_LATITUDE);
		colLastTimestamp = cursorCells.getColumnIndex("last_" + Schema.COL_TIMESTAMP);
		colLastLon = cursorCells.getColumnIndex("last_" + Schema.COL_LONGITUDE);
		colLastAlt = cursorCells.getColumnIndex("last_" + Schema.COL_ALTITUDE);
		colLastHead = cursorCells.getColumnIndex("last_" + Schema.COL_BEARING);
		colLastSpeed = cursorCells.getColumnIndex("last_" + Schema.COL_SPEED);
		colLastAcc = cursorCells.getColumnIndex("last_" + Schema.COL_ACCURACY);
		// [end]

		long startTime = System.currentTimeMillis();

		// get generic infos, basically mcc
		if (cursorCells.moveToFirst()) {
			long i = 0;

			fileNameMcc = cursorCells.getString(colMcc);
			cursorCells.moveToPrevious();

			// go back to initial position
			cursorCells.moveToPrevious();
		}

		long outer = 0;
		// serialize
		while (!cursorCells.isAfterLast()) {
			long i = 0;
			while (!cursorCells.isAfterLast()) { 
				// creates files of 100 wifis each
				Log.i(TAG, "Cycle " + i);
				String fileName  = mTempPath + generateFilename(mUser, fileNameMcc);
				saveAndMoveCursor(fileName, headerRecord, cursorCells);

				i += CELLS_PER_FILE;
				generatedFiles.add(fileName);
			}
			
			// fetch next CURSOR_SIZE records
			outer += CURSOR_SIZE;
			cursorCells.close();
			cursorCells = mDbHelper.getReadableDatabase().rawQuery(CELL_SQL_QUERY,
					new String[]{String.valueOf(mSession),
					String.valueOf(outer)});
		}

		long difference = System.currentTimeMillis() - startTime;
		Log.i(TAG, "Serialize cells took " + difference + " ms");

		cursorCells.close();
		cursorCells = null;
		mDbHelper.close();

		return generatedFiles;
	}


	/**
	 * Builds a valid cell log file. The number of records per file is limited (CHUNK_SIZE). Once the limit is reached,
	 * a new file has to be created. The file is saved at the specific location.
	 * A log file file consists of an header with basic information on cell manufacturer and model, software id and version.
	 * Below the log file header, scans are inserted. Each scan can contain several wifis
	 * @see <a href="http://sourceforge.net/apps/mediawiki/myposition/index.php?title=Wifi_log_format">openBmap format specification</a>
	 * @param fileName Filename, including full path
	 * @param headerRecord Header information record
	 * @param cursor Cursor to read from
	 */
	private void saveAndMoveCursor(final String fileName, final LogFile headerRecord, final Cursor cursor) {

		// for performance reasons direct database access is used here (instead of content provider)
		try {
			cursor.moveToPrevious();
			
			File file = new File(fileName);
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsoluteFile()), 30 * 1024);

			// Write header
			bw.write(XML_HEADER);
			bw.write(logToXml(headerRecord.getManufacturer(), headerRecord.getModel(), headerRecord.getRevision(), headerRecord.getSwid(), headerRecord.getSwVersion()));

			long previousBeginId = 0;
			String previousEnd = "";

			int i = 0;
			// Iterate cells cursor until last row reached or CELLS_PER_FILE is reached 			
			while (i < CELLS_PER_FILE && cursor.moveToNext()) {

				final long beginId = Long.valueOf(cursor.getString(colBeginPosId));

				final String currentBegin = positionToXml(
						cursor.getLong(colReqTimestamp),
						cursor.getDouble(colReqLon),
						cursor.getDouble(colReqLat),
						cursor.getDouble(colReqAlt),
						cursor.getDouble(colReqHead),
						cursor.getDouble(colReqSpeed),
						cursor.getDouble(colReqAcc));

				final String currentEnd = positionToXml(
						cursor.getLong(colLastTimestamp),
						cursor.getDouble(colLastLat),
						cursor.getDouble(colLastLon),
						cursor.getDouble(colLastAlt) ,
						cursor.getDouble(colLastHead),
						cursor.getDouble(colLastSpeed),
						cursor.getDouble(colLastAcc));

				if (i == 0) {
					// Write first scan and gps tag at the beginning
					bw.write(scanToXml(cursor.getLong(colTimestamp)));
					bw.write(currentBegin);
				} else {
					// Later on, scan and gps tags are only needed, if we have a new scan
					if (beginId != previousBeginId) {

						// write end gps tag for previous scan
						bw.write(previousEnd);
						bw.write(CLOSE_SCAN_TAG);

						// Write new scan and gps tag 
						// TODO distance calculation, seems optional
						bw.write(scanToXml(cursor.getLong(colTimestamp)));
						bw.write(currentBegin);
					}
				}

				/*
				 *  At this point, we will always have an open scan and gps tag,
				 *  so write cell xml now
				 *	Note that for performance reasons all columns except colIsServing and colIsNeigbor
				 *	are casted to string for performance reasons
				 */
				bw.write(cellToXML(
						cursor.getInt(colIsServing),
						cursor.getInt(colIsNeigbor),
						cursor.getString(colMcc),
						cursor.getString(colMnc), 
						cursor.getString(colLac),
						cursor.getString(colCellId),
						cursor.getString(colStrengthDbm),
						cursor.getString(colNetworkType),
						cursor.getString(colPsc)));

		
				
				previousBeginId = beginId;
				previousEnd = currentEnd;

				i++;
			}

			// If we are at the last cell, close open scan and gps tag
			bw.write(previousEnd);
			bw.write(CLOSE_SCAN_TAG);

			bw.write(CLOSE_LOGFILE);
			// ensure that everything is really written out and close 
			bw.close();
			file = null;
			bw = null;

		} catch (IOException ioe) {
			cursor.close();
			ioe.printStackTrace();
		}
	}

	/**
	 * Generates cell xml
	 * @return
	 */
	private static String cellToXML(final int isServing, final int isNeighbour,
			final String mcc, final String mnc, final String lac, final String cellId, final String strength, final String type, final String psc) {
		final StringBuffer s = new StringBuffer(WIFI_XML_DEFAULT_LENGTH);
		if (isServing != 0) {
			s.append("\n\t\t<gsmserving mcc=\"");
			s.append(mcc);
			s.append("\"");
			s.append(" mnc=\"");
			s.append(mnc);
			s.append("\"");
			s.append(" lac=\"");
			s.append(lac);
			s.append("\"");
			s.append(" id=\"");
			s.append(cellId);
			s.append("\"");
			s.append(" ss=\"");
			s.append(strength);
			s.append("\"");
			s.append(" act=\"");
			s.append(type);
			s.append("\"");
			s.append("/>");
		}
		
		if (isNeighbour != 0) {
			s.append("\n\t\t<gsmneighbour mcc=\"");
			s.append(mcc);
			s.append("\"");
			s.append(" mnc=\"");
			s.append(mnc);
			s.append("\"");
			s.append(" lac=\"");
			s.append(lac);
			s.append("\"");
			s.append(" id=\"");
			s.append(cellId);
			s.append("\"");
			s.append(" psc=\"");
			s.append(psc);
			s.append("\"");
			s.append(" rxlev=\"");
			s.append(strength);
			s.append("\"");
			s.append(" act=\"");
			s.append(type);
			s.append("\"");
			s.append("/>");
		}
		
		return s.toString();
	}

	/**
	 * Generates scan tag
	 * @param timestamp
	 * @return
	 */
	private static String scanToXml(final long timestamp) {
		StringBuilder s = new StringBuilder(32);
		s.append("\n<scan time=\"");
		s.append(timestamp);
		s.append("\" >");
		return s.toString();
	}

	/**
	 * Generates log file header
	 * @param manufacturer
	 * @param model
	 * @param revision
	 * @param swid
	 * @param swVersion
	 * @return
	 */
	private static String logToXml(final String manufacturer, final String model, final String revision, final String swid, final String swVersion) {
		final StringBuffer s = new StringBuffer(130);
		s.append("\n<logfile manufacturer=\"");
		s.append(manufacturer);
		s.append("\"");
		s.append(" model=\"");
		s.append(model);
		s.append("\"");
		s.append(" revision=\"");
		s.append(revision);
		s.append("\"");
		s.append(" swid=\"");
		s.append(swid);
		s.append("\"");
		s.append(" swver=\"");
		s.append(swVersion);
		s.append("\"");
		s.append(" >");
		return s.toString();
	}

	/**
	 * Generates position tag
	 * @param reqTime
	 * @param lng
	 * @param lat
	 * @param alt
	 * @param head
	 * @param speed
	 * @param acc
	 * @return position tag
	 */
	private static String positionToXml(final long reqTime, final double lng, final double lat,
			final double alt, final double head, final double speed, final double acc) {
		final StringBuffer s = new StringBuffer(POS_XML_DEFAULT_LENGTH);
		s.append("\n\t<gps time=\"");
		s.append(reqTime);
		s.append("\"");
		s.append(" lng=\"");
		s.append(lng);
		s.append("\"");
		s.append(" lat=\"");
		s.append(lat);
		s.append("\"");
		s.append(" alt=\"");
		s.append(alt);
		s.append("\"");
		s.append(" hdg=\"");
		s.append(head);
		s.append("\"");
		s.append(" spe=\"");
		s.append(speed);
		s.append("\"");
		s.append(" accuracy=\"");
		s.append(acc);
		s.append("\"");
		s.append(" />");
		return s.toString();
	}

	/**
	 * Generates filename
	 * Template for cell logs:
	 * username_V2_250_log20120110201943-cellular.xml
	 * i.e. [username]_V[format version]_[mcc]_log[date]-cellular.xml
	 * Keep in mind, that openbmap server currently only accepts filenames following the above mentioned
	 * naming pattern, otherwise files are ignored.
	 * @return filename
	 */
	private String generateFilename(final String user, final String mcc) {	
		// TODO: filename collisions possible, if called in less than a second
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
		formatter.setCalendar(mTimestamp);
		mTimestamp.add(Calendar.SECOND, 1);
		return "V2_" + mcc + "_log" + formatter.format(mTimestamp.getTime()) + "-cellular.xml";
	}



}
