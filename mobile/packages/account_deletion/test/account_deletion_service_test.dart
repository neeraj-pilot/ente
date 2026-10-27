import 'package:dio/dio.dart';
import 'package:ente_account_deletion/src/services/account_deletion_service.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('rejects untagged challenges before making a request', () async {
    final requests = <RequestOptions>[];
    final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))
      ..interceptors.add(
        InterceptorsWrapper(
          onRequest: (options, handler) {
            requests.add(options);
            handler.resolve(Response(requestOptions: options));
          },
        ),
      );
    final service = AccountDeletionService(dio);
    for (final challenge in [
      'header.payload.signature',
      'auth-token01234567890123456789012345678901',
      'delete-jwt',
      'delete-jwXheader.payload.signature',
    ]) {
      await expectLater(
        service.deleteAccount(challenge: challenge, reasonCategory: 'other'),
        throwsFormatException,
      );
    }
    expect(requests, isEmpty);
    await service.deleteAccount(
      challenge: 'delete-jwtheader.payload.signature',
      reasonCategory: 'other',
      feedback: 'feedback',
    );
    expect(requests.single.data, {
      'challenge': 'header.payload.signature',
      'reasonCategory': 'other',
      'feedback': 'feedback',
    });
    expect(requests.single.method, 'DELETE');
  });
}
