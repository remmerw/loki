package io.github.remmerw.loki

import io.github.remmerw.buri.BEReader
import io.github.remmerw.loki.core.DataStorage
import io.github.remmerw.loki.core.buildTorrent
import io.github.remmerw.loki.core.createMemory
import io.github.remmerw.loki.data.MetaType
import io.github.remmerw.loki.data.UtMetadata
import io.github.remmerw.loki.data.UtMetadataHandler
import io.github.remmerw.nott.createAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.fail
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class TorrentParserTest {
    private val path: String = "src/commonTest/resources"

    @OptIn(ExperimentalUuidApi::class)
    // @Test 
    fun parseTorrent(): Unit =
        runBlocking(Dispatchers.IO) {

            val content = "d8:announce40:udp://tracker.leechers-paradise.org:696913:announce-listll40:udp://tracker.leechers-paradise.org:6969el34:udp://tracker.coppersurfer.tk:6969el33:udp://tracker.opentrackr.org:1337el23:udp://explodie.org:6969el31:udp://tracker.empire-js.us:1337el26:wss://tracker.btorrent.xyzel32:wss://tracker.openwebtorrent.comel25:wss://tracker.fastcast.nzee7:comment34:WebTorrent <https://webtorrent.io>10:created by34:WebTorrent <https://webtorrent.io>13:creation datei1490916601e8:encoding5:UTF-84:infod5:filesld6:lengthi140e4:pathl21:Big Buck Bunny.en.srteed6:lengthi276134947e4:pathl18:Big Buck Bunny.mp4eed6:lengthi310380e4:pathl10:poster.jpgeee4:name14:Big Buck Bunny12:piece lengthi262144e6:pieces21100:  �x�o�b;-��'�?'#=��6��c��'3�eb�W�+�뙲�>�p�ǉBΛĔaܟ���ߓ������@������O�
���o        ���:��vX#f}��"�j��A_�ULX��&���>����mX��u���w�P��*.�tq���V8&�3B�Y΢�
�#���N`:��1���.�tE�� �kYl�i���{Xۏ�� 7|1�C��n��*�9� ��C��mM$�o���s�|�I'�܄k�ϻ���T����^���Lh�&ԁ/3ߌ�O\X��fCJ`q��Ӟ0�P�5�Jv>��' Qo��v0&�wTX杋b�v�@��1F,7��NK��J]/��hy=������[��5vVh����6�m���'�vՊRw��MH��e!Yr=H�
ro�I���ˬ�m���6�P�p�hs���w��*�������^Z�v�GOikHDUE�B��P�c�{�Cy��[\ ��&�C�,*d<ވI�is��������R������#��S�>Ĉ�-��a��+ �s34���_�/�G(7�Ƭ���5˔5��Q+��'�!<����jȪ        `
$3d��5�`��������C���L3]μ�W�M�c%�D�ȓ��J        9���@�,7��%P_�J��"xaA�Y�6t��08{�Z��V'i����.M��E��H�>����Lt�"N�y��v ��s!�H�F%���ᜋ��_��F��
F��&+���ޗ���U⽣z1
RԭfH��]����G�F��9�Or�vǦ��26�Oc��8��^i:;��#�EF��]R�0�#�q���v�2�����4}x7�qׯ�2Йx��H�1��m*9UZ�B�@kc���ґU��fQ�уz����]        ��I��q:k$_�VydW�Y|�H��2��Z�*��@ţD�{uBK)��dL��(A0���vˀ$1Z��'�2Q�@ڐ~`�O�dǰ���w��CuT�*)�v{�8#K�C���!}u�u��O�'�I�_!s���&��iMGs<n���o���Н�ߛ����S�#��坷WT�C5%�}��kkB�t"tx^â���.�pj�F�䠾3���(�o��չ)2�A|�K�oP?�i���:��BЊ�ϥW"�0�=)������$�ݡ� Cl���{C؅Q:���e����!��36Y��ID 7���f����>���Ak��p:�q=X��\ǳ�ߣ��C�il��vJ�+��<�W�p0�J�o�!�.�4,��P��eEۋ^�q�N�$���'̚�~��/`��Ã'D� �#�c4��>w��T���Јl�+�#t�A�A!M�P}Q�F�|i�}o�XU�ڢ0�'���s�e���z���k��+��~?��2����        ��������I8>�^��܌����am�:��l'��ۺD�?P�N�C�����a�DW!X}x6-�a�3D�        7��@(��pñ�8��y�B�1�������0elD�#��Q���M�c�y�ʏ�$\��lpge�ٯe��Zi�ѭ�>�]4�I]���l!yR��o�S���av.��-��`�6�*��Ki�p��j��[�Y���C|}�5�}���s��3�:H��H�}���w�� 1)��r|<��T�phr�\=�'�n[������Ϲ~�        �:��%T��%�c�����R���3�H��t�����_h0׆ Mbn�]^`��4q����A�u���؇B�(1��@��
���g�<�u�к����% �J���������0�22lP�X��xf{��q��|-�im3`�4L���>ĵ!�������.ʀ>lK�L�Q0��'k�n
WH�P����c��c��M�tFaF̓z�߁�����N�h�o�p"AnG�
�칥�u�u�1�.��V�Qв�v�s��g��P#@��c%���>@����/v�C\3�8�E�ʳ8�=�D'�1�k4���-�~zQޛ�Nfҭ�X�ǿ�=g�d�'o`�c�,��Q���€N���X���u��ػ���OSSȗђ��b�w��6q��=#?Y�y��R6�h+˿�N���t�,/��6���"���<�W�A�H�L���s���޷4�6&neiuO鐥W�
v˟/4��^͔d8���W�(���&�����A�v��n ��� �Ϭ�3�&�Q�*�#�Z��]�`�n8�D�Ԩ;>�_��>qka�x�)�����$�X�ś!�8e�����w�AFZG�j���<�Mf������%��vm�, �^}I:�}N�áCp3���)���!`��>;���|h�K�,�ԾS��Atr���t�[��|�kZ�w���
n�<$�_M/��        �m��zS�&�����>���:��Fe3����@&#e�m�g��\1�����x�C��������ڻι�ʕ@Jg�F;l����$}�
�����7��tE�~�M�t�         mp"8�Hه������I��/�x����-ͧR�� �W�d�!4��\}'�%���kU�g_F��#U��0�4�0���aF��ph��Gǯ�vn���ܾ�� 'u�!L
��b�)I�0Sڴ
�7-\        ���&�Ngh�?50>p"�z�+*N�&���S��}�KY��%6i]am�τ�(i#�����6���"�̏�?�D���>��!_����q��mZV��k5�����6�n�n���J���(��i@�Sp7O����O�ꔐ���q���x����&g�9[#T�V�e�T�w�)�y�kܨE������w2�~O�!Y"#_=f��s��l�k���&_��IY2i Ŕ�¦��|�9
���>9��7����>����֭~*K򑥺k��)��M��k����S���e�G�m$b�tWq�����KUl�x���p����w�RQ��<�����7�q,�$���?XW=��0W����nQ+C��־��������84�*��0{�1�;�䚮p`���d԰�˖bp��ג_��n!w;�k*4�|B�5r���!1��7<��f�a;V`��9^��܇JѴ�zPw��M��ɜ�n9`0��Mlp���<"߈�W`8W5�' /τf��        y�޽����ɭ��u�9�69�i\��o�ʑ�,�?g9�EM}�����F�����nP�N�0��n�6$ދ?�$�&�U�(�n�
��862�"B���n�p��IL~z���Z��pœ|�*���~�8~u��M�Q��}������G�]�+�����?d�c�$U�T�R��ͤߞ� BS+m���H޹�}Wz(K�&�?Z�:��QJD���b��wq�x������Dan]0�1�)o���#���@�
�� v;�m,C��P�J�m�T��Ua�U�k?��F����i��+�<�c�eJ��f����E�Nd2�ϊ�J=�F�@}����Խ��8D�^:r�ZZ˙b        ˆUѹ���K]�8x�&jR�̟:�$#��V��Ļ��/�O��K��@�M��Q�:�ֵ�`_�����u����]w�ߕ���:4�����!C.Q�;m%I
���~+����&9��^{���}�>_���韡{9H�^19��J�8B���[)��+/��=8�.�ժ9�1k���̯J���e�V�Xj�Gn�@j��gt���(�Z�/x��(X!d�nj�omD���V2��sー�h�3�U�F�7�xT$�i�5���v�����A%�Ɂ�E"O�ߦ� 3�9|�6����kS-j�� �i �+���L�>i��[��R!�5�qB�H�F        ̒�J���j��ǘ�0�c83Cf��ދ�ƛwj��*�o"mDR�
S#�zX��L2>.>4��f��2�/bM�H"�s��Z��O�����{�����3~\�f� ĭuC�4r��Wi��`1���)�tٻ�S�!�(}$��:�_{�C^@y������'��D��|K�PH�c�kA��<�MW�aV�e(�U���"�oHb�Ix?;�"Qg�⮤lbN�P���x9���n��!���m�~ZG��ޣ�`9��5��A��\�.�O�?��#���]+��,<z|.(I?h��}I���G�2�[O��C6{n���>ohN��2�2x^c+4De���rC$Gd�K�|\B�1�1�D�#O����dL�7��mތ�A���/���˒̋����T>~�� $`�/G�!�w�^���8����{���k���~�R�U���`��]��QϲD�̎lM���흡�1{2�v�m�� �*��v�8-%_��GM����M�Θ�A���,S��)B@��~�gиkLm��8�� ޸z��_{ �\8�+{���L?&(�� �MM����G1K�a�H��sF,Ihm
J��%�#9=R/�������j�7��z�t���"�d�)I*�����q���ę�`���gMԇ*H1Zǈ)�uE�b�n{�3�Olv�����K�h�(�?�+͵�2�-        P�g��렠;=%��12O6İE��E��FLU�%���9l��V����w����_�@��AѲHP�q�>�܁�&�8?�
�⧁�%gzJ�:0�v��r���̅K܂F��wqK�ָ���$�c�V{C��4oFdx����cE^���Uv�ܮ�X N��y�!^��d���ؒ�%�,y�?L��ީ��偄��������>�BK6��j:�����o�KФv:k �H�z~ <�޽����8Z��ͱ����o"��I�u'�|��]),��z�s)Fs�94 '�$�?7K��������h�`�)<��Jq�H��
O(^5D�pdƱh/�����X0���вp������w�A�z���S�γ��z���1>g ��DH���fC9��s��+�$ͭZ���9�Y�����0tg��v��ח2�JV�eoL `kZ�-I5 �.����'j�?$�T�u�ٽ��[�L��A����;�IV��Z�҂�2�l;���Z���G���p4���w�$��"R6(���}y����4F=Y\x���f�^
���        ��ȷ�Oc1��y���4�i�~ d�GF��C�t����`c�mUn��5��f�����ngC�dгO�b��q9���­����7��+�֎��jR0��f�_�v���M��^��G�a�K`��w�ق�\;,�t6��l�.�W9V{�q��z�Q}�yUk�.jU�����= ��"nvy��������K��Z���8n���#����b�+��� 
�UkK�qY�N�崠Mh�^r�9��ݼs!�SL0n�����va@�zDz�Ä�O�}E"Lk�4�5�8��芈���=�4����i`�~Dv��y'� Wu��tX�'�        C���n��sX��e�WT���櫖����
l���H�e��/无0<+�)�Wq�}tL��vC�u���k�_���DҚ�ip�A@�'��3�k��T��y�N�3H)���l�c��-틬v!H�fb�����+��qx�x��
�4�VLX��7P>�4�KP C4�T^��TV��QqQ��6���K�:��OSJ�VO��2Ie�?��A�]q)T3D���m�0rL΅���#g��3�7d!<{ᮡ"�DT��g�%��
HB�Ni����uH���<�e%��u_R� "(�,2�����w-�꼆
        A͋�6������Ǧ9g��>i�Jfċ슒�ye�X�}��㶦����)�h`I��f�mĽ��Ʒ��y4"j�׮l9@�        �*�ZWغ��w�z7U����6�[J��i����� m,YB͟�C������|V�Pth]_̍�%=Jo:O8�^�����@�PQ�{��a�}P�@cW��-;V}[x���ak9�"        (]�����ו���s��<9�4�7��&���"Ur���P3S���~��r��&Y��>`��ȕ��Kt��kJT��H�o ����A
����9ik>��m.&�a��F �o        ��o��D��N�-9���_Ii�mo��Ȳ-7A�8@���9���ŒZ.
.�Jq�PN�f�A�C+�/O:�w�7������Ŝ��ުDz��M��!ڕ�NA�lD_���4�p%�[��K)�a\��վ�9���w���#XyZ��������20�\�e��b0�m��)        ��g*�,K���a�=����^��./U'o��bK�iG�@�q���c��YO(��!^d�7Mg����}@I����Tq�vO�μ�.����6g/����0���킷��0x��]TL��:�ը@P�,����h��������{C=1�GDF]6톳α���Pw�Z��:Wϟ4���������NS���m>*��C��W7� ���hQ�        ����+�&�$��+��������#`=�
��1�Z�C�A�rW��p?>�3N�x&�݅b|�?"��z�:N�[;?*yS�t����ǗU�ۛjn!��#�/R|���J�;���qkdP�_���S�vR�T����i.��5�Ϲh�m�Çr�-8�����O��B�x.��Is�f�f���m����5�%;���!q��ʪغǔ�0���mc�ht����E+x�~/Q�"��[����E��=�&*�� �H�zo\׍�l��l?�����g���9w]ى�NR���D�T�N������0�n�����u�\Z2߄��G��܇�m3VL/�xI�N"��0�h�����i�D��j��)g��q����������E~�k[0�.R6�-�>=6�=���pm�e��Cї J��r�J5���!KV�rV��lq‚C�l'��Z羒r��\*=u� �p����N��������|�"�����ޱ��nQ���z�w*��G7�vA        �]Y&�ۊX�f��Q
[�,k���d��>�&�8�qz�m�z����e�s[Ѥ8�х��앤'i�s1�LU�L_�'}�`"!\�~��P���a�T\u        ꜰ�        �J%��
˝0�Ф���\����[�qGeP�I6�����Ʈ+�E`�[��N��|�NG���p7L䮖Eٳ&����*5�=h�6SUT��3)�������b�c����S�A �lY�a��Wc����F���(��x�3�Q%-?j|6狖_�7�(��-�
��խu\� ����.�;P_<[�n��:���13�|��������%,@’m����<k"�J���F��$�/�����"M�߶�R�7�x\糟*&~ }�4]Y�����"u$N���N� ÝD�#K��Us�2�z�����W�G�         \������|����=3%��;���A��a�*���s���Vt��h�تFt����54W���j֬�R�[v�k�z��<�_��ȉ��q��t8p~��
KXU_����i�8�%ܞ�����yi"x��Til���ڄ�yoV�y��b�R�Ph�=a�Z%V�0X���W&�(�T8HV�Q��4i`��`�����h_�U�ɣ�Q9����l$^!w�.�b��෇�����D�[��4��i�P� ���y�Èݻ�F��Z�7k�P���?��C[w��Ƈb�w����p���#�NO��r]n��-?��D*\�n��
��R�j�v�k,��+�?����tK�3"�<Z���y?�M�}e�\�����/�O]K���L3��/�A�T�R�e~X���WL�-�����F�͆�H�,X��� 9x ���xV��Yd        �/�AQD�<�K'���'Ű�D�}�o��'��o�t��a����os$���ٺ��_        ��
�>�AO5�8�I���#A~�D�22��~͎m(Y���6Yg�U�uZuƬ!��9yILN�d��8����������y�����S6���yΉ�m9��~��+�˕�E�u~aT��J0gY�W�C�a�@�t!_?L����'@�М�w����Pl�b���U��:��X���J-����0?�p'�h�*����W�Wi���r�#�
�6>n>�<�˒�����Yɭ��i���\U�v�lI��!ִڌ���*�^"�fIΓ쯦W�|��"���myK
.+�<G*Of�3w�+��\MQ:�D�S�N�y-���|̕|���#� W#M>��!�K�\~N���oR~�{h�C/�C3e���C_�[7~|�@���HoMM�`6����+C1OxHXt��6��4��!��c��Kgb٧,�_���w��.T���|�J���<*A�-���&ۢ        oؽ��7D�I���e3�66y�w1���AW ��c�_c�\�#Q�������2�?s�h�h��o�׆���Y�8�H_���(fZ�L��S ĭ��!iO�/Ia�1��BU�.#@�8:q{_n�T�Yep<�~�-9�:2b����^�\ ц������[_����A'�����e�=��|(%x��B�p�d7�:���(���r�V�~��Mm�lP����P;^Xo�%Sw����L���j����d`��x$rUƸU���>�5)� �b���2�[:�RM��'(r[�=ȶ�H�E�o��(�Դ�J�O^�^<�CI^�3;���G=L_��;1�Χ��D#�wL.z�(d��k�5����.=��$���rf�������ͅS&�vj~`��Dk.��+%�ϋ�
�̲�l@L�+�a[�#��!�`&,JqM�(P庣�MOI�-Պ�����g�;C t[���ũ_c�x��+�k�ڟ�ǰD�lj>ECoWnZl        �"������~.4�6��]�75kY>cP݂g��*k�
3I�)��Ż�X^K��Ϊ�mE��4��)�P��C�g��V����ĽNۂ_�¤�����0�ҫ��٢����$5�Wus(�M�5���G�)�=���3���$���9�(7y���.%��Ob�p��C�Ym���(P�EjJ�w�/���^о        
�;4E�ԖN�H���)��L�%����›��"�n��c�^̺0,��(��=�h��(��7=+� �ܐ��˲(Z�y@�Df��tho�P3��uK3��\tr/�k��J]r� �-޴*[�(N��r����Mv�����1��ZY��H��!�K��=O�����#X���x��}�-�%��9�k�[@n��A���Q�}hg0K�j�j^�Y�j��~I�ƣ8B�fú��~��08�C��[ݭ2���IM�eT��0�^�%�~J�����s���$`Y'EK{�+��Y4�����›y��au�)Ỏ�$��/X��0*�'?�4����ȪN��2VDgk��{���e�k�.@������cV�z+.���ֈ�#�G��+_)ͧB��2,��'�5� q���֨������uv@�ho�i���! �j�'������|Ҫ"������Rf���sy�0���x�����QpڱF���a�=�eҐ��[��F����Y���.�#3��Cbk+�E��uP�r�t�/HF���"������j
�J}��&��g��o"�~� 8��1&ng�S ʛ��j0���6�        ��ML:@Q1�785c5?O�ا^nW���~�U��A#f����Ȟav1X �*�sm?iy}{`�eRS�l飂8;}.'�^�f���*h%LH3�P���
D~X�5��A���ۮ��v����pL�v=��]$��l�T[�,��
�        ����z��}�����*8z%���>�=i��b���UrK        �¶�fգ<��(��P        ͊7��^lWA�'�Վ8�FA^�Y�~��ER-Ϧ����9d霑��ԆJ`���&��5�i��&�f��
 ��J�D3b���A��zc�v H&_w�.VC!| �������?\=�Ξ�Q��/��*s!1�V������������ǐ쟋tI�:lR�L{K�����;B�5�#}�^/56l-m&��M�F�������E�?��~s�����l@S���_F7�z�X�Py�
&.$��Zӯ��)��OW��Z��i4���`��X�fh�n[k�40�d���HL�3Y/Ʌ0�$DTm�<��|6G�<        �iβ��.'��WU`F�!9��}�.y��A�I^�Ü�V�?�$&�I        Dށ�<�B\�v#�p����D?R��NM���,eQ�Z�L���)>�q�6^��r�?����hXU�@i38��v��xk�Y�z�j�̍��6n�;��k�D�1�ƈ'����Zg��]��u�2���u�u,�.@Q>kΤb i/_JY1�
�������l��X1�S��X�        L#Y��s�f3J�u\��d        ����72��E^|�z����*�� avS5ɭ���U�:��o4��׊�S/3���ZN�
��l����S
7u$�������QD����Zw��Ǽ8ߩ��K�s6l����p�������S(�t����h�e�'Yf+E+VC�'$�z��`J yK��Z:QP� �J�e�k"�~СEV�*y���dm�O�OG�Q�^pz
��`b��A,.�5�U_�`�ѡ��{��/��V���F�JIc���� L���������f��y�/v:�{a��=�M�y�H>��}מ��j��:p��m�sj���8::��$M�l���tu"��1�8�Q�;p?��<��-�.X!��P���<��5r�C1I��j�s��S��]���c�%%H��'R����m�`�')�;/�E��`x��5R��4Lӄ!��M%�l{`���� �O��Y*rZ�Ѱ[>:��KQ��ғ>����. ��EXv�V_cj�;w�{肯�\�Y�����-p��p�����������_&5�N�aT��wHiC�aT����F�e�����R���-d���ݮ'�ߝA`�xY��6[�Dno���h�
~�[�2�����؅ޔ��B�X��        �ը�ن��d�n�i�G�����R���Ԓ0r֘��őǘ��봚}�wp��^���æ����~��p7Œ�2{�ɖrn��!}upErUF��1��d���Ѽ�        �ϟ��w?KPw�M">U��tm>%d�Gߚ�{�`���~b�Z�iw7`nXG�`        �M�kW#Ԓ�ACO8?\xz���ʇ��-�&��/�RL6��"        M)_����w'��D>�[���7�����L���-���K:|�        �VP������W=|�W�wu2���UT�(x���$[N����Uo��)T������ ��׺n��L"n ���s{�Z'R]�u�A�e ϫrD\��}�7R��        +�>��D"M^0�^�3��;�n~�t��V��t@�}� G�+�
��p�=�xF��v��MR4�%3.��os�F�)R`�*�:$Σ��%��X
��eV8��Q�H[��#��|DV%޸�պ�s�IP�����ξ�<�~��p\)�p��e��]� �zw��l}"a��Y�[�V��"Hu��$�);�FoW�̺B�ZQg���kj��B�4C �Hu.����;���!o� 4��Jr�ʔ�B����*�rys��3�[�:ak.+���_q��[s^�|�-��n��Q�g�������2�f���Ts%b��v���O���TI���IMHv���        �����_kB�\N͞ǝ饶��T멦�w!����.�A�o��37�(,��=�O]@��O��V���.ya�i$�������r�����Bc�E�b|v�(b�]p��y�W�s�!X��k���a�>��C��*�R��rÛ�A�~�>34}�G��X��"��y5����'Q���6�[?�9�K�Lg�7R1�",�,��������YfIu���\�        �l�D�^���6P��%�.<�_�10Q�9<u�7�5V������f}c�gԍC��@-dt<=a=����$o��!%��Ј�I�Tt�7Z�d�̄f�@��E$瑬���\<�c����>/�?=�����!��q�����b�(������إ��bZ=W='�w�^���,=7�~��J��3��7@�ֵ�^|�l�rW�1��)        [W� ���E���p��m>-i���QnW�w���{ŭ        ��r�����bO�\��U@��-|�ӣ��(��{��1���
���k�f}k��?���n�$���Ԯ��fD�
|�k
�Vf���#��I�t�j����S�i�dZh4�����[��� �7S�DU>���� �����:oOn"�
UQ����Ŀ�5�i��l4�=Iv��L?�G��a,�[�ưU4)�j��; eFq>��X]��uA���.vZ��8���ǯ=;�?"�qr �̄8C        j�Un�@[�#�Y~�4�:E����i$�3E��C$7�쇋[�|q�X ~�����e��%3���e�c1b�˺Y����n�J���k�|L���?=��ڕ|3���sN�쯯x�P�!B�1~���_���d���R��e��\yT�w`��� mǭm�_Sæ����*��o4����Wֹwjv���9�ƙ��F��“gD�eqPk����<��:9}��!�*���l�(�F�H_��rf8G>���n�E����5ANp�2��e���@�IӔ��ӣC��D4垾|������\?X�h�T'�s,1G��y�n�u{�>U�2˓�����g��rn���eP�h�RkM�I��7��gF�r�e�L���͎'���h�X��=�+�l�L^��\b�^���DA-�Yy����vJ[        M�܄+��1�y�G���p���MA�pj��7Y�|�Ji)��^~tC#X�Z�鶓���'6=�qK�'dJB�ǹ��>͇�"���9���5�h��=�D���R���-d        r�4��%ˁf@S��k&��@�JH�-��X�Q>;@dA�(�x�D��9��4vꎫ��
h�8�i��9`���<豣t��y�ח���4���@o?X~��y�����f�@@���Wʠ��O��릟~P;/���pFa��A�l��P`��&2�|q��-Cw���Ԁ�� �j�\�a���xC�\�o!����R��zƀ!        ��†�<M� k���<�F2���θ<�C���V���jE�c�c�̙�5��� �x�{�(ͭ��ƻB�y�x�c�q"������0������|T5vWW�C�K��^��3��ĵV����V/�PRu4^2Pb�j�@�ExV���P��l�"�)���?���N��-��!��Si�f8�RGs����V*�)p���P)���,�̒(j��=a3�]}b�rp��+d�TD.�����<���\������8Ћ���p6�mc*�>�4�bA���:�ιt&���O☤K=�Z����BH#PL�䥉\���nb����F����B��X=4/K%��i�Pa\$ʃ�/�x�t��%SA^v��ti�)���HL��]\�S�愬#��D�ѣ�f��^����!X
�U�,T���p^�M7�6$'׀����&Nw��W��p        �8�"�+6M��Y1�v��� �3q"�٣W��>y��M�},fHWݥ��.(J����H�gu�ĵ��2�i��^~�Z����l�2�y;�WԌ��k���«{��_K��W�M��~7m�n��ElF&��)hj�a"�F���X^~��SU҃.��A�6��Rc�C� |���A?%o]��f��]Z�����w�BЬ![#��9g�z�� ��,=��;k
M\2�2Ѵq*��b�½*��\V�oEC8�9����7�����{r�����v�l��N-&"DhG���Ю�/y^��w rg�@"��*�8�6�ԗ�n�$��J�\� [����`����>������tE����4{�0��-n���ΦfS.%� ��`��y �z��p�V�
&x�ʨ�Z0�9D<        S�p7UΒ̋����c;p��U��&8���oƬu����vޘ�4o�t�H�=�;AY������{�Y�z/��H�U��ɗ}�23ѹ0�����G-83�eF�r�F��׼��%�P�+�l�]M����R����APi�$�����P�]O��~7� ^�����5�Eh<(͞6��Mb�-���T;����.d4KE�:��ȃG`�Zm�=P����)kM ��������
�|6�zd����%��G`�5�+aV]�����xJcy�~��������RXu�������ƈ�Ms5O�%k3(u��۝�ԫeXRYaJ(=��" ���ȍC�T!�Wp�
8FO�L�]�n���M����� ����z����7\E�֔�
�F��7��S+N��}fYv�'�����!d@v��R�[ߕ��������� W����'��Ţs        o|�ᕮվ;|��=z�.P�G�j�D�4NCT�1P �������:ޣHC��zaX�5�w2S@��8�ř�>��[����X/B`�n>֖f��z��Vp�;�h�Y�@�;T����(��h����_Ђ�yk��h/�󳎔�*��#��ؾ+j�F_s��W��Z-�+��9�~���6X�h��='X��*���0�0��0���p�*]y�A�=����<- ��g�pc.t�(�&x��g֟B��        a� �ſ�[�R��4���NA&�yP�ؒ��K�K<=-Dֈ]�u�&��P�=o��Js�0��-_�
14�L��$����ƕ���G;���kgeƒA�N8􉽿2��w����
�!��g��ޛ'X�\�荰�6������.�?�v�CX���]x��!bZ�����K��L�g�Fp���$dʁB�-a�wU�� !+�u/�f�tE�N|O�I�I�[Hx���c���E3��eU���5�s|��q��x����JE��N*4sXn:�3�G��N�+�����|�5i7�/��60��[��M�%� �E��F
K�V��,��(3�X.1j򽞖����*��)�=9�b;���읟��_T�<�'2"�'�`t*�p���,��S�o��c��%*��{��r��J�A��Ehh��q�pN f����T�g�{m��\}zZ7�~/3m��z��/o�/�(�m]}���d�%������d#�@�$I��������`�D9�d��b'��y���iRq�w�        G�&~��@3� I�ϱkj��@�)���Uݯ�A��)����XX���H�����t{I�@�3>�zGO��>�%'N������Deu�s�R���D��l��j�Pl�oT���`�� ��H/��S�\Y�T��#
U-7��ٕ�5BK�X}W���:Z���O�D���p�g�$8�;"�����        �]4o��y��kХ#��!zxLG���N��m/�������޸c�<fos-�蝈3��7��󳾝�\        �B�v��D\���G�^��W�hG�w�VA�p��nB>!$�qMC�5�����X-��p�Iu~GU��2ɀT��Oi&VⓀS��t��������@��Ⱥ*|D�I        {�f8�����(�S�֙PD�z��"x>1Č�G�5���5����ˑ���'2��>�mbK��X��'F��Τ��D�)��=�<Y�@]���7~�E���c��I�6��`��.ļ�t�v�=�{Њ�2M�˨��U����l$�k�51�:���u�<u/_���)H�t��$�[N�u�2��gpM��
.��*�vPL6=~�#�g��P#� ��n 3����iC\�j��jѼ�'#%���"N�dӧ��ba!�-���m#I��
���n1���I �E�r}��hgR�ԕ        �G<
M���Jw,�E~��+�z��מM����j>�J.iC/�o%ɶCf��A�        ef��*[��0��F���׿�y���D���V{�1��f\Q�+�Y��{
���&�{�7����S�H�4�X�B�7Á{߄�IX�o��5��~�,Ͷ@����m�����`�)|
\�W��a����e"��
���VGf�b���Đ�%��l�����p,jX�t��ͬ�/#x�#!<���5�ȱ�F��O�Es��s�q��r        _T�$�m;�a=9�1��~[8��M�ͽ��}hhNp��%{�gx�]:�=mQ��=�T�� �eɼ�Ao�f��,�+i:
�
�e�d�he#��C��!o~_���X�J+R�        E��f-3g�ެ`���j '��fH�-�m/�x_��M<N���
̀��h����        /�/�S�~~�"yp<�NjT��4\$�3�vrW��1��)�������<����~d�9��\��xT�[��m�P���&�EH����%ʼ�F7P�ޥ��U�]������<�?ص�����~��Y.�t&���OD�����q�o)�<��f��Z/��:V��j���������4��)���L����|����-6ӫC��������R8���)nֽ52t^�@��}���x^h�},��b�5C>q����!�˩��W���X���z�d�.�\%�3�7�!��5yT��Y󅕛;jE�ŭ��s5��W7Z��zW���\��z����^�<�� ��6����E�m7b�CA�V�jJF�i7a�n(o����m��m_�X"P;�ܵ*'_S���=yIZ�m�V����m�y��9#8�z����g��{5��        &�V|���Xն�\���!\�=oK�@O�_�X��8�� �Bv���W����)�}:øښ���g����˺�X���sA�I�:��v�E��5n�7Z�9�Ad��c�ǳ�c,�����~�zX(���WZ�����@��%Ȳ]��ܥ��        -�q�\|Q����~�OK�W�=
��q�ц���zֳ���wZ���IMo�]��1��嶄�F���.p�+w�ҟ���~�U��s;���c�9�V�_���+GS8� ��C����T��o�쌞<!H��e�����Y���2��bB��*��}�D>��͹���.�/�Pݝ�?@(0�4�.�N��}5D`5��ofV�52<-��:��W �q�% M����di�3�a����.��q�*��̣jD'm��3���dd�3��+���T5�f�!�y�& �p�����<>[��1Z���q�s���a�=���5AZ���F        ���c���X�h}��c�8����JUՈD��oolP���[���zx�&1P����f�P��N=OV�3eUF������ι�a;A�a���+�G�5|�@!U�[5���#�+-q�q-}j���M���U�;/�S�h�aPe�r��DM��,=�K��V`��P+t���y�1�s@���!�#�����`(�WQ�Ju��$v�*�i+��r��P�-�BL&���24E�"2�$3bNKͼ�͜{_^?/����4�^*���/�=������z�8���3�G���6!���C&����0J���Z튊��0�����LR�H�����w��vji��I4��I�"O1
��0��Ր�}�IHT�
[�dpӬ��<\uÑ±5x����4��#
��H
h'�����T4�a���7��hM��A����g=W֮R��d�ӻuM��_�O���t剃�ʼ�����{y�D���Z��3��VUF̳���a��%�Z�ܜW����;� ��+5��}&[{C�h�5F���^<����L鹒���q�T q�6{�o(�_�m}���_s%�lp���x*L<�/X�-�{�Up��&ƢB��++�SP�Y��n��Aӽ9�fp��'�� X{�$�t�}�ծ)��JaK ��Uo��ҩD�5���{�L�XҜ׎�KKV��dO�0/�@��\�*����k����*�9�.L�Q        V�8%:�HXT-�[Ф�V�&��ξa�θ��        8'�ipRݱe�F#&sp���>�����q-&��QN�x�)��QYοF�p�h�{����H����p,�[�!��#��!�"V�]�E�W���� �0L<�f{��:��!3�L�b�����e2׾��ή��        MTfo��7<����^�l*P�R�6y�D���$�9uCS��g�F��}��K7o�|=��$�WY�?���i��a��F�ܱ[6�!TSw��b�X��Ro�UkB�����QE��3=��ڟy���o3A��� ��Ƕƃ�¸i)��'�        ��[�����~��� ��BG�1�Mdnԋ�vpEԒ���p�g�_ 9wnߦ���8�+gl��l��W�2�ą�dQ �I��t���>~��@�9Ik�ĉh"�:Xf0w_�PYf�r��\�?:,�P\Ƹ}e�ܹ�܀j�=���U���� !�\q��7�7�E���r������=C��a�sS�����-Q˿����w�W���˷�Md�,� ���Y�lBC���/X5�.���~��.���DK��AI�\B|�"-�,��l-3��]+�mY�C�        W��{P�'�I*�?Z�&�T�_��b�*���7e'�O�.����[��d2?�@kzd\����EJAr�����        ��������L:��R;��u��P0���N�E�^�'��I&��V�m
��q�.q��T�����u�MS���Vĳ���+���Xt��V!4�Һ�s�,ֹ��u�-�"        %|5M�s�F��3=M,�K����ᘓ��F|V��Z%O���Y�r���,��AnZ��+�&���#G"�1!~��b�6N㲙x,�,f[��ѫ�^��2Ա��x؏��,���C���xx ���i�W        �J@��j�P#t3��q�:�<A Ju�2:�j���aR��5�2��5�V��W�,��RO6�����V��3,��O�r����c!Z�����WO��ML_h��L�q2���y��G���t� ��(|=����wċ�
;Y����1���/Ɲ��dLvh���p��s��e        `�'��,�c�Aw;W��QRj��M�\>�d��m^]��t��{�
�o9��]�pr�[������Rl�j�(�C���I3�_����&��9s�<��OԌa�H?1g���:�YU�I�@���r�WKUC��y���ջ2>��6���YQ�"�M"I��
*"        �RL��+-$�>/���bk�{;�V�E{%i�unI��{)��{�%� '�　@*Lhm$���`S�2�Dԭy��Pq�c��t�<        ?f�        �إ�¾͎N��8t��B>ר����2�A�Lð����ZP/L~P�5�88cP��3Qگ�i�Ž��I�4��|�7SJ�        ,a� ��}<`pPY��O�a\{9�mͪG�[���������̼Y����ʆ>T4^ho�P��eOt�V�
��/�(�=��`S��7ڗ�k���}�"e�q64�^F������������{+/S��S{Z K�QKZ���)�)���v<�����(�v1(�3?Ա���ng���mMr�;8�����!����X�4�z�        P���UMecN�V�ipXihǐ���'h�_��Ic�e���t_�x��1>|�
!��=��v��qS+Kk�AA�M�)N�Ea)K.�0�E��,�q�q�t�c�sʼͫs0;��DX�miǽF�,1�y������=��ۆ�]捺D�uf�"߶������F��视��        ݐ�2�V'�������C/<`D�u�F*��X�W� ��<�T
�1N'�*c�����oM�N��h��Sk�j����$CZ��+)�������5�.�8p܀+�8��8���G7V�ΔR=�r¡�������j���*S�܈F�>V�"������V���c�O8��O�ꠋT��b/- �@�v��X�Ʀ�����P������־��t��fD[�M�3܉���(x��@��.�Dʦ��<���*Ѱ�N`qx.}v��/ۣ_�gR�s��&C���0�ĵ��T3E�.�!`��Ȁ�7�C���:UJ����C˜���U��7�Z�l�)yl��L�+�f��H�c:}1s�gl�g�oD� ���_B�d-�n)���wc����\�~+v��(�S�<H�$w�������\�SgF�>���q�����Mݼ>�Jk�[ֶ sը_PjJP�������~��"������!nH�����2�d���U���冮���G��C���03��_Ě�ˇM���d�!��z��P��Ó���Ǩ볠�o����)nv�(��E�(�����C9,|�ۖx�,�>*�ޠ�        ���F)�C��1�������2F6q
��B�?a��h��:�F4?����*��h�쿪wZ�����*��e�#D�>9�-�e�HQo�jI�T�$�V�yh<y����@c&�3�Z�ߴ��-�K��LYB����?�:���,���Fr���tH<Ů�s>�!l7�{߭�3P���W+�V}f�6�E���~���� R��g�_�uhN��m�ƅ�����ِV�ΝNj�}$�,�8k�SXX6�ߴ���/�A���j�(3I,�vߊ-���
�Ө�m�O�4IΒ]�y9�؅H2��dX�'h�Jќ���N�<B����ǰVl);��Cj�rÍ*�nHNP��ާ~����?ۉ�܏*+�慮�qB�޲����iW���l4B�I��MgFP�����~NT�Z���F�e8:url-listl31:https://webtorrent.io/torrents/ee"
            

            val path = Path(SystemTemporaryDirectory, Uuid.random().toHexString())
            SystemFileSystem.createDirectories(path)

            val dataStorage = DataStorage(path)
            
            cal data = content.encodeToByteArray()
            val metadata = createMemory(data.size.toInt())
            metadata.writeMemory(data, 0)
            
            val torrent = buildTorrent(metadata)
            assertNotNull(torrent)
            
            dataStorage.metadata(metadata)
            dataStorage.initialize(torrent)
            val dataBitfield = dataStorage.dataBitfield()
            assertNotNull(dataBitfield)

            val files = dataStorage.torrentFiles()
            assertEquals(files.size, torrent.files.size)

            dataStorage.delete()
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testStorage(): Unit =
        runBlocking(Dispatchers.IO) {
            val path = Path(SystemTemporaryDirectory, Uuid.random().toHexString())
            SystemFileSystem.createDirectories(path)

            val dataStorage = DataStorage(path)

            dataStorage.verifiedPieces(10)

            dataStorage.markVerified(0)
            dataStorage.markVerified(5)

            assertTrue(dataStorage.isVerified(0))
            assertTrue(dataStorage.isVerified(5))
            assertFalse(dataStorage.isVerified(1))
            assertFalse(dataStorage.isVerified(9))

            dataStorage.close()
            dataStorage.delete()
        }

    @Test
    fun testMetadata() {
        val data = Buffer()
        val utMetadata =
            UtMetadata(
                MetaType.DATA,
                0,
                100,
                ByteArray(500),
            )
        val handler = UtMetadataHandler()

        val peer = createAddress(byteArrayOf(10, 20, 30, 40), 999.toUShort())

        handler.doEncode(utMetadata, data)

        val bytes = data.readByteArray()
        val buffer = ByteBuffer.wrap(bytes)
        val reader = BEReader(buffer)

        val result = handler.doDecode(peer, reader)
        assertEquals(result, utMetadata)
    }
}
