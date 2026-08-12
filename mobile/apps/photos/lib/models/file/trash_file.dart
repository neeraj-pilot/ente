import 'package:photos/models/file/file.dart';

sealed class TrashFile extends EnteFile {
  TrashFile();

  TrashFile.from(super.file, {required this.deleteBy}) : super.from();

  // time after which will will be deleted from trash & user's storage usage
  // will go down
  late int deleteBy;
}

class EnteTrashFile extends TrashFile {
  EnteTrashFile();

  EnteTrashFile.from(
    super.file, {
    required super.deleteBy,
    required this.createdAt,
    required this.updateAt,
  }) : super.from();

  // time when file was put in the trash for first time
  late int createdAt;

  // for non-deleted trash items, updateAt is usually equal to the latest time
  // when the file was moved to trash
  late int updateAt;
}

class DeviceTrashFile extends TrashFile {
  DeviceTrashFile();
}
